package nz.ac.aut.comp713.fieldtasks;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Real HTTP transport. Every method is called on MainActivity's worker executor. */
public final class ApiClient {
    private final String baseUrl;
    private final String token;

    public ApiClient(String baseUrl, String token) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.token = token;
    }

    public static String normalizeBaseUrl(String value) {
        String result = value.trim();
        try {
            URI uri = new URI(result);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Enter an HTTP(S) API address, for example http://10.0.2.2:8080/comp713-a3/api/");
        }
        return result.endsWith("/") ? result : result + "/";
    }

    public JSONObject login(String username, String password) throws Exception {
        return object(request("POST", "auth/login", new JSONObject()
                .put("username", username).put("password", password)));
    }

    public JSONArray projects() throws Exception {
        Object data = request("GET", "projects", null);
        if (!(data instanceof JSONArray)) throw new IOException("The server returned an invalid project list.");
        return (JSONArray) data;
    }

    public JSONArray tasks(long projectId) throws Exception {
        Object data = request("GET", "projects/" + projectId + "/tasks", null);
        if (!(data instanceof JSONArray)) throw new IOException("The server returned an invalid task list.");
        return (JSONArray) data;
    }

    public Task task(long id) throws Exception {
        return Task.fromJson(object(request("GET", "tasks/" + id, null)));
    }

    public Task update(Task task, JSONObject changes) throws Exception {
        changes.put("version", task.version);
        return Task.fromJson(object(request("PATCH", "tasks/" + task.id, changes)));
    }

    private JSONObject object(Object value) throws IOException {
        if (!(value instanceof JSONObject)) throw new IOException("The server returned an invalid JSON object.");
        return (JSONObject) value;
    }

    private Object request(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = read(input);
            JSONObject envelope;
            try { envelope = new JSONObject(response); }
            catch (JSONException exception) {
                throw new ApiException(status, "HTTP_" + status,
                        "Payara returned HTTP " + status + " without JSON. Check the API base URL and deployment.");
            }
            if (status < 200 || status >= 300) {
                JSONObject error = envelope.optJSONObject("error");
                throw new ApiException(status,
                        error == null ? "HTTP_" + status : error.optString("code", "HTTP_" + status),
                        error == null ? "The request failed (HTTP " + status + ")." : error.optString("message", "Request failed."));
            }
            if (!envelope.has("data")) throw new IOException("Missing data in the server response.");
            return envelope.get("data");
        } finally { connection.disconnect(); }
    }

    private String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 2 * 1024 * 1024) throw new IOException("The server response is too large.");
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static final class ApiException extends IOException {
        public final int status;
        public final String code;
        public ApiException(int status, String code, String message) {
            super(message); this.status = status; this.code = code;
        }
    }
}
