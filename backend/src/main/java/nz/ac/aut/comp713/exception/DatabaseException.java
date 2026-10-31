package nz.ac.aut.comp713.exception;

/** Unavailable/locked/corrupt databases become HTTP 503, without leaking paths. */
public final class DatabaseException extends RuntimeException {
    public DatabaseException(Throwable cause) { super("Database operation failed", cause); }
}
