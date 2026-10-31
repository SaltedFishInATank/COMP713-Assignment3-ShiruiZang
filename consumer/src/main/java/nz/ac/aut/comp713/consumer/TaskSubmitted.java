package nz.ac.aut.comp713.consumer;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.StringReader;

/** A message contract, not a shared database model. */
record TaskSubmitted(String eventId, long taskId, long projectId, String title, String workerId,
                     String completionNote, String occurredAt, String payload) {
    static TaskSubmitted parse(String payload, String headerId) {
        JsonObject json;
        try (var reader = Json.createReader(new StringReader(payload))) {
            json = reader.readObject();
        }
        String eventId = json.getString("eventId");
        if (eventId.isBlank() || !eventId.equals(headerId) || eventId.length() > 64) {
            throw new IllegalArgumentException("Message eventId must equal the non-empty JSON eventId.");
        }
        if (!"task.submitted".equals(json.getString("type"))) {
            throw new IllegalArgumentException("Only task.submitted is supported.");
        }
        long taskId = json.getJsonNumber("taskId").longValueExact();
        long projectId = json.getJsonNumber("projectId").longValueExact();
        String title = json.getString("title");
        if (taskId < 1 || projectId < 1 || title.isBlank()) {
            throw new IllegalArgumentException("A task submission requires positive ids and a title.");
        }
        return new TaskSubmitted(eventId, taskId, projectId, title,
                json.get("workerId").toString().replace("\"", ""),
                json.getString("completionNote", ""), json.getString("occurredAt"), payload);
    }

    String notification() {
        return "Review needed: task #" + taskId + " \"" + title + "\" in project #" + projectId
                + " submitted by worker " + workerId + ". " + completionNote;
    }
}
