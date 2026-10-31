package nz.ac.aut.comp713.model;
import jakarta.json.Json;
import jakarta.json.JsonObject;
public record User(long id, String username, String displayName, String role) {
 public boolean coordinator() { return "coordinator".equals(role); }
 public JsonObject toJson() { return Json.createObjectBuilder().add("id",id).add("username",username).add("displayName",displayName).add("role",role).build(); }
}
