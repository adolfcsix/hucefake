package vn.huce.service.chat;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import vn.huce.model.ChatConversationSnapshot;
import vn.huce.model.ChatExchangeReceipt;
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
    void ownedConversationPreservesHeaderAndChronologicalExchanges() {
        when(repository.findOwned(student, 7L)).thenReturn(new ChatConversationSnapshot(7L, "Lịch học"));
        when(repository.exchanges(7L)).thenReturn(List.of(
                Map.of("id", 2L, "question", "Sau", "created_at", "2026-11-30T08:00:00.000Z", "reply", "{}"),
                Map.of("id", 1L, "question", "Trước", "created_at", "2026-11-30T07:00:00.000Z", "reply", "{}")));
        when(proposals.decode("{}")).thenReturn(Map.of());
        assertEquals(Map.of("id", 7L, "title", "Lịch học", "exchanges", List.of(
                Map.of("id", 1L, "question", "Trước", "created_at", "2026-11-30T07:00:00.000Z", "reply", Map.of()),
                Map.of("id", 2L, "question", "Sau", "created_at", "2026-11-30T08:00:00.000Z", "reply", Map.of()))),
                service.conversation(student, 7L));
    }

    @Test
    void newMessageUsesPersistedIdentifiersAndKeepsExchangeContract() {
        var input = Map.<String, Object>of("message", "Thông báo");
        var reply = new LinkedHashMap<String, Object>();
        reply.put("message", "Không có thông báo.");
        when(replies.answer(student, "NOTICES", null, "Thông báo", input)).thenReturn(reply);
        when(repository.createConversation(student, "Thông báo")).thenReturn(7L);
        when(proposals.encode(reply)).thenReturn("{}");
        when(repository.insertExchange(7L, "Thông báo", "{}", null))
                .thenReturn(new ChatExchangeReceipt(8L, "2026-11-30T08:00:00.000Z"));
        assertEquals(Map.of("conversationId", 7L, "exchange", Map.of(
                "id", 8L, "created_at", "2026-11-30T08:00:00.000Z", "question", "Thông báo", "reply", reply)),
                service.message(student, input, new ChatClassification("NOTICES", false)));
        var order = inOrder(repository);
        order.verify(repository).createConversation(student, "Thông báo");
        order.verify(repository).insertExchange(7L, "Thông báo", "{}", null);
        order.verify(repository).touch(7L);
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
