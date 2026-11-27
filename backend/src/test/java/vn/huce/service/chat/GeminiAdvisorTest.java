package vn.huce.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.huce.exception.ApiFailure;

import static org.junit.jupiter.api.Assertions.*;

class GeminiAdvisorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiAdvisor advisor = new GeminiAdvisor(mapper, "test-key", "gemini-test");

    private String response(String action) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("candidates", List.of(
                java.util.Map.of("content", java.util.Map.of("parts", List.of(
                        java.util.Map.of("thought", true, "text", "ignore this"),
                        java.util.Map.of("text", mapper.writeValueAsString(java.util.Map.of("action", action)))))))));
    }

    @Test
    void parserIgnoresThoughtPartsAndAcceptsPermittedAction() throws Exception {
        assertEquals("GENERATE", advisor.parseAction(response("GENERATE"), List.of("GENERATE")));
    }

    @Test
    void parserRejectsActionOutsideAllowlist() {
        assertThrows(IllegalStateException.class,
                () -> advisor.parseAction(response("APPLY"), List.of("GENERATE")));
    }

    @Test
    void requestSchemaUsesExistingAllowlistAndDoesNotContainApiKey() {
        var body = mapper.valueToTree(advisor.requestBody("Xếp lịch", List.of("GENERATE", "UNRELATED"), "classify"));
        var allowed = body.path("generationConfig").path("responseJsonSchema").path("properties").path("action").path("enum");
        assertEquals(mapper.valueToTree(List.of("GENERATE", "UNRELATED")), allowed);
        assertFalse(body.toString().contains("test-key"));
    }

    @Test
    void disabledAndInvalidConfigurationFailBeforeNetworkRequest() {
        GeminiAdvisor disabled = new GeminiAdvisor(mapper, "", "gemini-test");
        GeminiAdvisor invalid = new GeminiAdvisor(mapper, "key", "../invalid");
        assertEquals("ASSISTANT_DISABLED", assertThrows(ApiFailure.class,
                () -> disabled.classify("test", List.of("GENERATE"), "classify")).code());
        assertEquals("ASSISTANT_CONFIGURATION", assertThrows(ApiFailure.class,
                () -> invalid.classify("test", List.of("GENERATE"), "classify")).code());
    }
}
