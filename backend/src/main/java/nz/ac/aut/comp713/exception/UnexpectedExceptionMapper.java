package nz.ac.aut.comp713.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception error) {
        LOG.log(Level.SEVERE, "Application request failed", error);
        if (error instanceof DatabaseException) {
            return ErrorResponses.json(503, "database_unavailable",
                    "The database is temporarily unavailable. Please retry shortly.")
                    .header("Retry-After", "1").build();
        }
        return ErrorResponses.json(500, "internal_error", "An unexpected server error occurred.").build();
    }
}
