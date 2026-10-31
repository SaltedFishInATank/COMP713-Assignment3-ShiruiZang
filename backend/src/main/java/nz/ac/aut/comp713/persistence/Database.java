package nz.ac.aut.comp713.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;
import nz.ac.aut.comp713.exception.ApiException;
import nz.ac.aut.comp713.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** File-backed embedded H2, owned by the WAR; each operation closes its connection. */
@ApplicationScoped
public class Database {
    private final Path databasePath;
    private final String jdbcUrl;
    private volatile boolean initialized;

    public Database() { this(defaultPath()); }

    /** The path is a database base name, without the .mv.db suffix. */
    public Database(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
        if (this.databasePath.toString().contains(";")) {
            throw new IllegalArgumentException("The database path must not contain a semicolon.");
        }
        jdbcUrl = "jdbc:h2:file:" + this.databasePath.toString().replace('\\', '/')
                + ";LOCK_TIMEOUT=1000;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=10";
    }

    private static Path defaultPath() {
        String configured = System.getProperty("comp713.a3.db.path");
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        String domainRoot = System.getProperty("com.sun.aas.instanceRoot");
        if (domainRoot == null || domainRoot.isBlank()) {
            throw new IllegalStateException("Run on Payara or set the comp713.db.path JVM system property.");
        }
        return Path.of(domainRoot, "lib", "databases", "comp713-a3", "tasks");
    }

    @FunctionalInterface
    public interface SqlWork<T> { T execute(Connection connection) throws SQLException; }

    public <T> T read(SqlWork<T> work) {
        try (Connection connection = connect()) {
            return work.execute(connection);
        } catch (SQLException error) {
            throw new DatabaseException(error);
        }
    }

    public <T> T transaction(SqlWork<T> work) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        } catch (SQLException error) {
            if ("23505".equals(error.getSQLState())) {
                throw new ApiException(409, "duplicate_project", "A project with this name already exists.");
            }
            throw new DatabaseException(error);
        }
    }

    /** Keep the engine warm for ten idle seconds; release its file on undeploy. */
    @PreDestroy
    public synchronized void close() {
        if (!initialized) return;
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException error) {
            java.util.logging.Logger.getLogger(Database.class.getName())
                    .log(java.util.logging.Level.WARNING, "Database shutdown failed", error);
        } finally {
            initialized = false;
        }
    }

    private Connection connect() throws SQLException {
        initialize();
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    private synchronized void initialize() throws SQLException {
        if (initialized) return;
        try {
            Class.forName("org.h2.Driver");
            Files.createDirectories(databasePath.getParent());
            try (InputStream input = Database.class.getResourceAsStream("/schema.sql")) {
                if (input == null) throw new IOException("Missing bundled schema.sql");
                String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                        .replaceAll("(?m)^\\s*--.*$", "");
                try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                     Statement statement = connection.createStatement()) {
                    for (String sql : schema.split(";")) {
                        if (!sql.isBlank()) statement.execute(sql);
                    }
                    nz.ac.aut.comp713.config.Seed.apply(connection);
                }
            }
            initialized = true;
        } catch (ClassNotFoundException | IOException error) {
            throw new DatabaseException(error);
        }
    }
}
