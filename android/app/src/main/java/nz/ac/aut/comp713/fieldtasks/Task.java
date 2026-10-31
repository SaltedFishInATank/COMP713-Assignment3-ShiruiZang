package nz.ac.aut.comp713.fieldtasks;

import org.json.JSONException;
import org.json.JSONObject;

public final class Task {
    public long id;
    public long projectId;
    public long version;
    public String title;
    public String description;
    public String status;
    public String assigneeName;
    public String completionNote;
    public Double latitude;
    public Double longitude;

    public static Task fromJson(JSONObject json) throws JSONException {
        Task task = new Task();
        task.id = json.getLong("id");
        task.projectId = json.getLong("projectId");
        task.version = json.getLong("version");
        task.title = json.getString("title");
        task.description = json.optString("description", "");
        task.status = json.getString("status");
        task.assigneeName = json.isNull("assigneeName") ? "Unassigned" : json.optString("assigneeName", "");
        task.completionNote = json.isNull("completionNote") ? "" : json.optString("completionNote", "");
        task.latitude = json.isNull("latitude") ? null : json.getDouble("latitude");
        task.longitude = json.isNull("longitude") ? null : json.getDouble("longitude");
        return task;
    }

    public String statusLabel() {
        switch (status) {
            case "todo": return "Ready to accept";
            case "in_progress": return "In progress";
            case "submitted": return "Awaiting coordinator review";
            case "done": return "Approved · complete";
            default: return status;
        }
    }
}
