package nz.ac.aut.comp713.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** JAX-RS routing and content-negotiation failures must also use the JSON envelope. */
@Provider
public class HttpExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException error) {
        int status = error.getResponse().getStatus();
        String code;
        String message;
        switch (status) {
            case 400 -> { code = "bad_request"; message = "The request could not be understood."; }
            case 404 -> { code = "not_found"; message = "Resource not found."; }
            case 405 -> { code = "method_not_allowed"; message = "This HTTP method is not supported for this resource."; }
            case 406 -> { code = "not_acceptable"; message = "Use Accept: application/json."; }
            case 413 -> { code = "payload_too_large"; message = "The request body exceeds the 16 KiB limit."; }
            case 415 -> { code = "unsupported_media_type"; message = "Use Content-Type: application/json."; }
            default -> { code = status >= 500 ? "internal_error" : "http_error";
                message = status >= 500 ? "An unexpected server error occurred." : "The HTTP request failed."; }
        }
        Response.ResponseBuilder response = ErrorResponses.json(status, code, message);
        String allow = error.getResponse().getHeaderString("Allow");
        if (allow != null) response.header("Allow", allow);
        return response.build();
    }
}
