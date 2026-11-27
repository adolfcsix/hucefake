package vn.huce.service.chat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.dto.ChatClassification;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.ChatRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private final ChatRepository repository = mock(ChatRepository.class);
    private final ProposalService proposals = mock(ProposalService.class);
    private final ChatClassifier classifier = mock(ChatClassifier.class);
    private final ChatReplyService replies = mock(ChatReplyService.class);
    private final ChatService service = new ChatService(repository, proposals, classifier, replies);
    private final Actor student = new Actor(1L, "student", "STUDENT", null, null);

    @Test
    void inaccessibleConversationCannotReadExchanges() {
        when(repository.findOwned(student, 7L)).thenThrow(new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu."));
        assertEquals(404, assertThrows(ApiFailure.class, () -> service.conversation(student, 7L)).status());
        verify(repository, never()).exchanges(anyLong());
        verifyNoInteractions(proposals, classifier, replies);
    }

    @Test
    void messageRechecksOwnershipAfterClassificationBeforeCreatingProposalOrExchange() {
        when(repository.findOwned(student, 7L)).thenThrow(new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu."));
        assertEquals(404, assertThrows(ApiFailure.class, () -> service.message(student,
                Map.of("message", "Thông báo", "conversationId", 7L, "semesterId", 9L),
                new ChatClassification("READ_NOTICES", false))).status());
        verify(repository).findOwned(student, 7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(proposals, classifier, replies);
    }
}
