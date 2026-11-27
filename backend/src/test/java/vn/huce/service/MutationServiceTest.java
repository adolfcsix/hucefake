package vn.huce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.IdempotencyRecord;
import vn.huce.repository.MutationRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MutationServiceTest {
    private final MutationRepository repository = mock(MutationRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MutationService service = new MutationService(repository, mapper);
    private final Actor actor = new Actor(1L, "staff", "STAFF", 2L, 3L);

    private MockHttpServletRequest request(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/course-sections");
        if (key != null) {
            request.addHeader("Idempotency-Key", key);
        }
        return request;
    }

    private String legacyFingerprint(MockHttpServletRequest request, Object body) throws Exception {
        var data = Arrays.asList(request.getMethod(), request.getRequestURI(), actor.role(),
                actor.unitId(), actor.staffId(), request.getParameterMap(), body);
        String serialized = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(data);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(serialized.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void noKeyStillAcquiresLockWithoutStoringResponse() {
        assertEquals("ok", service.write(actor, request(null), Map.of(), () -> "ok"));
        verify(repository).lock();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void malformedKeyNeverInvokesOperation() {
        @SuppressWarnings("unchecked") Supplier<Object> operation = mock(Supplier.class);
        assertEquals(400, assertThrows(ApiFailure.class,
                () -> service.write(actor, request("invalid key"), Map.of(), operation)).status());
        verifyNoInteractions(operation);
        verify(repository).lock();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void newRequestStoresCanonicalLegacyFingerprintAndResponse() throws Exception {
        MockHttpServletRequest request = request("save-1");
        request.addParameter("scope", "personal");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("z", 2);
        body.put("a", 1);
        Map<String, Object> result = Map.of("id", 7);
        assertEquals(result, service.write(actor, request, body, () -> result));
        var order = inOrder(repository);
        order.verify(repository).lock();
        order.verify(repository).deleteExpired();
        order.verify(repository).find(1L, "save-1");
        order.verify(repository).save(1L, "save-1", legacyFingerprint(request, body), "{\"id\":7}");
    }

    @Test
    void matchingLegacyRecordReplaysWithoutMutation() throws Exception {
        MockHttpServletRequest request = request("save-1");
        Map<String, Object> body = Map.of("name", "A");
        when(repository.find(1L, "save-1")).thenReturn(
                new IdempotencyRecord(legacyFingerprint(request, body), "{\"id\":7}"));
        @SuppressWarnings("unchecked") Supplier<Object> operation = mock(Supplier.class);
        assertEquals(Map.of("id", 7), service.write(actor, request, body, operation));
        verifyNoInteractions(operation);
        verify(repository, never()).save(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void changedBodyWithSameKeyIsRejected() {
        when(repository.find(1L, "save-1")).thenReturn(new IdempotencyRecord("other", "{}"));
        @SuppressWarnings("unchecked") Supplier<Object> operation = mock(Supplier.class);
        assertEquals(409, assertThrows(ApiFailure.class,
                () -> service.write(actor, request("save-1"), Map.of("name", "B"), operation)).status());
        verifyNoInteractions(operation);
        verify(repository, never()).save(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void failedOperationDoesNotSaveSuccessfulResponse() {
        assertThrows(IllegalStateException.class, () -> service.write(actor, request("save-1"),
                Map.of(), () -> { throw new IllegalStateException("failed"); }));
        verify(repository, never()).save(anyLong(), anyString(), anyString(), anyString());
    }
}
