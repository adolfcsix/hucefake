package vn.huce.service.chat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.dto.ChatClassification;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.ChatRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatClassifierTest {
    private final ChatRepository repository = mock(ChatRepository.class);
    private final GeminiAdvisor gemini = mock(GeminiAdvisor.class);
    private final ChatClassifier classifier = new ChatClassifier(repository, gemini);
    private final Actor student = new Actor(1L, "student", "STUDENT", null, null);

    @Test
    void recognizedScheduleDoesNotCallGemini() {
        assertEquals(new ChatClassification("SCHEDULE", false),
                classifier.classify(student, Map.of("message", "Lịch học hôm nay")));
        verifyNoInteractions(gemini);
    }

    @Test
    void studentCannotSubmitDraft() {
        assertEquals(403, assertThrows(ApiFailure.class, () -> classifier.classify(student,
                Map.of("message", "Tạo lớp", "draft", Map.of()))).status());
        verifyNoInteractions(gemini);
    }

    @Test
    void checksConversationScopeBeforeReadingPreviousIntent() {
        when(repository.findOwned(student, 7L)).thenThrow(new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu."));
        assertEquals(404, assertThrows(ApiFailure.class, () -> classifier.classify(student,
                Map.of("message", "Ngày mai", "conversationId", 7L))).status());
        verify(repository, never()).previousIntent(anyLong());
        verifyNoInteractions(gemini);
    }

    @Test
    void followUpUsesPreviousIntentAfterOwnershipCheck() {
        when(repository.previousIntent(7L)).thenReturn("SCHEDULE");
        assertEquals(new ChatClassification("SCHEDULE", false),
                classifier.classify(student, Map.of("message", "Ngày mai", "conversationId", 7L)));
        var order = inOrder(repository);
        order.verify(repository).findOwned(student, 7L);
        order.verify(repository).previousIntent(7L);
        verifyNoInteractions(gemini);
    }

    @Test
    void rejectsGeminiActionOutsideRolePermissions() {
        when(gemini.enabled()).thenReturn(true);
        when(gemini.take(1L)).thenReturn(true);
        when(gemini.classify(anyString(), anyCollection(), anyString())).thenReturn("APPLY");
        assertEquals(new ChatClassification("DENIED", false),
                classifier.classify(student, Map.of("message", "abc xyz")));
    }

    @Test
    void unavailableGeminiFallsBackWithoutExecutingAction() {
        when(gemini.enabled()).thenReturn(true);
        when(gemini.take(1L)).thenReturn(true);
        when(gemini.classify(anyString(), anyCollection(), anyString()))
                .thenThrow(new ApiFailure(503, "ASSISTANT_DISABLED", "Unavailable"));
        assertEquals(new ChatClassification("UNRELATED", true),
                classifier.classify(student, Map.of("message", "abc xyz")));
    }

    @Test
    void exhaustedQuotaReportsFallbackWithoutCallingRemoteModel() {
        when(gemini.enabled()).thenReturn(true);
        when(gemini.take(1L)).thenReturn(false);
        assertEquals(new ChatClassification("UNRELATED", true),
                classifier.classify(student, Map.of("message", "abc xyz")));
        verify(gemini, never()).classify(anyString(), anyCollection(), anyString());
    }

    @Test
    void disabledGeminiStillAllowsLocalFallback() {
        assertEquals(new ChatClassification("UNRELATED", false),
                classifier.classify(student, Map.of("message", "abc xyz")));
        verify(gemini, never()).take(anyLong());
        verify(gemini, never()).classify(anyString(), anyCollection(), anyString());
    }
}
