package nz.ac.aut.comp713.api;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import nz.ac.aut.comp713.exception.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

/** Parse bounded UTF-8 JSON ourselves so malformed/scalar payloads always produce useful JSON errors. */
public final class JsonBody {
    public static final int MAX_BYTES = 16 * 1024;

    private JsonBody() { }

    public static JsonValue parse(InputStream input) {
        try {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new ApiException(413, "payload_too_large", "The request body exceeds the 16 KiB limit.");
            }
            boundNesting(bytes);
            String body = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            try (JsonParser parser = Json.createParser(new StringReader(body))) {
                if (!parser.hasNext()) throw invalidJson();
                parser.next();
                JsonValue value = parser.getValue();
                if (parser.hasNext()) throw invalidJson();
                return value;
            }
        } catch (JsonException | CharacterCodingException error) {
            throw invalidJson();
        } catch (IOException error) {
            throw new ApiException(400, "bad_request", "The request body could not be read.");
        }
    }

    private static ApiException invalidJson() {
        return new ApiException(400, "invalid_json", "The request body is not valid JSON.");
    }

    private static void boundNesting(byte[] bytes) {
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (byte value : bytes) {
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            } else if (value == '"') quoted = true;
            else if (value == '{' || value == '[') {
                if (++depth > 64) throw invalidJson();
            } else if (value == '}' || value == ']') depth--;
        }
    }
}
