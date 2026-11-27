package vn.huce.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void errorJsonPreservesExistingFieldNamesAndDetails() throws Exception {
        var response = handler.failure(new ApiFailure(409, "VERSION_CONFLICT", "Tải lại",
                Map.of("version", 3)));
        assertEquals(409, response.getStatusCode().value());
        var json = new ObjectMapper().valueToTree(response.getBody());
        assertEquals("VERSION_CONFLICT", json.path("code").asText());
        assertEquals(3, json.path("details").path("version").asInt());
        assertDoesNotThrow(() -> UUID.fromString(json.path("request_id").asText()));
        assertFalse(json.has("requestId"));
    }

    @Test
    void duplicateAndOtherConstraintErrorsRetainHttpStatus() {
        var duplicate = handler.integrity(new DataIntegrityViolationException("duplicate",
                new SQLException("duplicate", "23505")));
        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals("DUPLICATE_REQUEST", duplicate.getBody().code());
        var other = handler.integrity(new DataIntegrityViolationException("constraint",
                new SQLException("constraint", "23503")));
        assertEquals(400, other.getStatusCode().value());
        assertEquals("VALIDATION_ERROR", other.getBody().code());
    }
}
