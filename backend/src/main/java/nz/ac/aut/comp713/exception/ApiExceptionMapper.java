package nz.ac.aut.comp713.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {
    @Override
    public Response toResponse(ApiException error) {
        return ErrorResponses.json(error.status(), error.code(), error.getMessage()).build();
    }
}
