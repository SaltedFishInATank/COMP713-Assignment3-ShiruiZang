package nz.ac.aut.comp713.model;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/** Domain value; internal duplicate-name keys never appear in API responses. */
public record Project(long id, String name, String description, String createdAt) {
    public JsonObject toJson() {
        return Json.createObjectBuilder().add("id", id).add("name", name)
                .add("description", description).add("created_at", createdAt).build();
    }
}
