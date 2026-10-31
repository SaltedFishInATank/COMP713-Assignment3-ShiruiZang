package nz.ac.aut.comp713.exception;

import jakarta.json.Json;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

final class ErrorResponses {
    private ErrorResponses() { }

    static Response.ResponseBuilder json(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Json.createObjectBuilder().add("error", Json.createObjectBuilder()
                        .add("code", code).add("message", message)).build());
    }
}
