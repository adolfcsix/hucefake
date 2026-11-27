package vn.huce.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.huce.exception.ApiFailure;

@Service
public class GeminiAdvisor {
    private static final Pattern MODEL_NAME = Pattern.compile("gemini-[a-z0-9.-]+");
    private final String key;
    private final String model;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final GeminiQuota quota = new GeminiQuota(System::currentTimeMillis);

    GeminiAdvisor(ObjectMapper json, @Value("${GEMINI_API_KEY:}") String key,
                  @Value("${GEMINI_MODEL:}") String model) {
        this.json = json;
        this.key = key.trim();
        this.model = model.trim();
    }

    public boolean enabled() {
        return !key.isEmpty() && !model.isEmpty();
    }

    public boolean take(long actorId) {
        return quota.take(actorId);
    }

    public String classify(String question, Collection<String> permitted, String instruction) {
        if (!enabled()) {
            throw new ApiFailure(503, "ASSISTANT_DISABLED",
                    "Cần cấu hình Gemini trên backend để dùng chế độ này.");
        }
        if (!MODEL_NAME.matcher(model).matches()) {
            throw new ApiFailure(503, "ASSISTANT_CONFIGURATION",
                    "GEMINI_MODEL trên backend không hợp lệ.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestBody(question, permitted, instruction))))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini returned an unsuccessful HTTP status");
            }
            return parseAction(response.body(), permitted);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception error) {
            throw unavailable();
        }
    }

    Map<String, Object> requestBody(String question, Collection<String> permitted, String instruction) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", question)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", Map.of(
                                "type", "object",
                                "properties", Map.of("action", Map.of("type", "string", "enum", permitted)),
                                "required", List.of("action"))));
    }

    String parseAction(String responseBody, Collection<String> permitted) throws java.io.IOException {
        JsonNode parts = json.readTree(responseBody).path("candidates").path(0).path("content").path("parts");
        StringBuilder output = new StringBuilder();
        for (JsonNode part : parts) {
            if (!part.path("thought").asBoolean(false)) {
                output.append(part.path("text").asText(""));
            }
        }
        String action = json.readTree(output.toString()).path("action").asText();
        if (!permitted.contains(action)) {
            throw new IllegalStateException("Gemini returned an unsupported action");
        }
        return action;
    }

    private ApiFailure unavailable() {
        return new ApiFailure(502, "ASSISTANT_UNAVAILABLE", "Gemini chưa trả về đề xuất hợp lệ.");
    }
}
