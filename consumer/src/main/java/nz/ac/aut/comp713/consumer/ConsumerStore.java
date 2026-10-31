package nz.ac.aut.comp713.consumer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Independent consumer-owned data. The consumer never opens the backend database. */
final class ConsumerStore {
    private final String jdbcUrl;

    ConsumerStore(Path databaseBase) throws Exception {
        Path absolute = databaseBase.toAbsolutePath().normalize();
        if (absolute.toString().contains(";")) throw new IllegalArgumentException("Invalid data path.");
        Files.createDirectories(absolute.getParent());
        jdbcUrl = "jdbc:h2:file:" + absolute.toString().replace('\\', '/') + ";DB_CLOSE_ON_EXIT=FALSE";
        Class.forName("org.h2.Driver");
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS delivery_audit (event_id VARCHAR(200) PRIMARY KEY, "
                    + "attempts INT NOT NULL, redeliveries INT NOT NULL, duplicates INT NOT NULL, "
                    + "last_outcome VARCHAR(40) NOT NULL, last_seen VARCHAR(40) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS notifications (event_id VARCHAR(64) PRIMARY KEY, "
                    + "task_id BIGINT NOT NULL, project_id BIGINT NOT NULL, title VARCHAR(500) NOT NULL, "
                    + "worker_id VARCHAR(80) NOT NULL, notification CLOB NOT NULL, payload CLOB NOT NULL, "
                    + "occurred_at VARCHAR(40) NOT NULL, processed_at VARCHAR(40) NOT NULL)");
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    int observed(String eventId, boolean redelivered) throws SQLException {
        try (var connection = connect()) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("MERGE INTO delivery_audit "
                    + "(event_id, attempts, redeliveries, duplicates, last_outcome, last_seen) "
                    + "KEY(event_id) VALUES (?, COALESCE((SELECT attempts FROM delivery_audit WHERE event_id=?),0)+1, "
                    + "COALESCE((SELECT redeliveries FROM delivery_audit WHERE event_id=?),0)+?, "
                    + "COALESCE((SELECT duplicates FROM delivery_audit WHERE event_id=?),0), 'RECEIVED', ?)")) {
                insert.setString(1, eventId);
                insert.setString(2, eventId);
                insert.setString(3, eventId);
                insert.setInt(4, redelivered ? 1 : 0);
                insert.setString(5, eventId);
                insert.setString(6, Instant.now().toString());
                insert.executeUpdate();
            }
            int attempts;
            try (var query = connection.prepareStatement("SELECT attempts FROM delivery_audit WHERE event_id=?")) {
                query.setString(1, eventId);
                try (var rows = query.executeQuery()) { rows.next(); attempts = rows.getInt(1); }
            }
            connection.commit();
            return attempts;
        }
    }

    /** Result and dedupe key are one atomic database commit, before the broker commit. */
    boolean save(TaskSubmitted event) throws SQLException {
        try (var connection = connect()) {
            connection.setAutoCommit(false);
            try (var existing = connection.prepareStatement("SELECT event_id FROM notifications WHERE event_id=?")) {
                existing.setString(1, event.eventId());
                try (var rows = existing.executeQuery()) {
                    if (rows.next()) {
                        try (var update = connection.prepareStatement("UPDATE delivery_audit SET duplicates=duplicates+1, "
                                + "last_outcome='DUPLICATE_SUPPRESSED' WHERE event_id=?")) {
                            update.setString(1, event.eventId()); update.executeUpdate();
                        }
                        connection.commit();
                        return false;
                    }
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO notifications "
                    + "(event_id, task_id, project_id, title, worker_id, notification, payload, occurred_at, processed_at) "
                    + "VALUES (?,?,?,?,?,?,?,?,?)")) {
                insert.setString(1, event.eventId());
                insert.setLong(2, event.taskId());
                insert.setLong(3, event.projectId());
                insert.setString(4, event.title());
                insert.setString(5, event.workerId());
                insert.setString(6, event.notification());
                insert.setString(7, event.payload());
                insert.setString(8, event.occurredAt());
                insert.setString(9, Instant.now().toString());
                insert.executeUpdate();
            }
            try (var update = connection.prepareStatement("UPDATE delivery_audit SET last_outcome='STORED' WHERE event_id=?")) {
                update.setString(1, event.eventId()); update.executeUpdate();
            }
            connection.commit();
            return true;
        }
    }

    void outcome(String eventId, String outcome) throws SQLException {
        try (var connection = connect(); var update = connection.prepareStatement(
                "UPDATE delivery_audit SET last_outcome=? WHERE event_id=?")) {
            update.setString(1, outcome); update.setString(2, eventId); update.executeUpdate();
        }
    }

    List<String> status() throws SQLException {
        List<String> lines = new ArrayList<>();
        try (var connection = connect(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM notifications")) {
                rows.next(); lines.add("PERSISTED_NOTIFICATIONS count=" + rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT event_id, attempts, redeliveries, duplicates, last_outcome "
                    + "FROM delivery_audit ORDER BY last_seen")) {
                while (rows.next()) lines.add("DELIVERY eventId=" + rows.getString(1) + " attempts=" + rows.getInt(2)
                        + " redeliveries=" + rows.getInt(3) + " duplicates=" + rows.getInt(4) + " outcome=" + rows.getString(5));
            }
            try (var rows = statement.executeQuery("SELECT event_id, notification FROM notifications ORDER BY processed_at")) {
                while (rows.next()) lines.add("NOTIFICATION eventId=" + rows.getString(1) + " " + rows.getString(2));
            }
        }
        return lines;
    }

    String[] lastMessage() throws SQLException {
        try (var connection = connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT event_id, payload FROM notifications ORDER BY processed_at DESC FETCH FIRST 1 ROW ONLY")) {
            if (!rows.next()) throw new IllegalStateException("There is no processed notification to replay.");
            return new String[]{rows.getString(1), rows.getString(2)};
        }
    }
}
