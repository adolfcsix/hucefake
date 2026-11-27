package vn.huce.service.chat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduleAssistantServiceTest {
    private final GeminiAdvisor gemini = mock(GeminiAdvisor.class);
    private final ScheduleAssistantService service = new ScheduleAssistantService(gemini);
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);

    @Test
    void studentCannotUseAssistantEvenWhenCallingServiceDirectly() {
        Actor student = new Actor(9L, "student", "STUDENT", null, null);
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> service.classify(student, Map.of("message", "Xếp lịch"))).status());
        verifyNoInteractions(gemini);
    }

    @Test
    void disabledGeminiDoesNotConsumeQuota() {
        assertEquals(503, assertThrows(ApiFailure.class,
                () -> service.classify(staff, Map.of("message", "Xếp lịch"))).status());
        verify(gemini).enabled();
        verifyNoMoreInteractions(gemini);
    }

    @Test
    void rateLimitPreventsModelInvocation() {
        when(gemini.enabled()).thenReturn(true);
        assertEquals(429, assertThrows(ApiFailure.class,
                () -> service.classify(staff, Map.of("message", "Xếp lịch"))).status());
        verify(gemini, never()).classify(anyString(), anyCollection(), anyString());
    }

    @Test
    void validRequestUsesOnlyExistingAssistantIntents() {
        when(gemini.enabled()).thenReturn(true);
        when(gemini.take(1L)).thenReturn(true);
        when(gemini.classify(eq("Xếp lịch"), eq(List.of("GENERATE", "APPLY", "UNRELATED")),
                anyString())).thenReturn("GENERATE");
        assertEquals("GENERATE", service.classify(staff, Map.of("message", "  Xếp lịch  ")));
        verify(gemini).classify(eq("Xếp lịch"), eq(List.of("GENERATE", "APPLY", "UNRELATED")), anyString());
    }
}
