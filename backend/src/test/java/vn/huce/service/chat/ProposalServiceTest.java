package vn.huce.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.dto.SectionDraftPreview;
import vn.huce.dto.SectionIdResponse;
import vn.huce.model.SectionSubject;
import vn.huce.model.UnreadNotificationSummary;
import org.mockito.ArgumentCaptor;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.ChatRepository;
import vn.huce.repository.NotificationRepository;
import vn.huce.repository.ProposalRepository;
import vn.huce.repository.SectionRepository;
import vn.huce.service.RunService;
import vn.huce.service.SectionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProposalServiceTest {
    private final ProposalRepository repository = mock(ProposalRepository.class);
    private final ChatRepository chat = mock(ChatRepository.class);
    private final SectionRepository sectionRepository = mock(SectionRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final RunService runs = mock(RunService.class);
    private final SectionService sections = mock(SectionService.class);
    private final ProposalService service = new ProposalService(repository, chat, sectionRepository,
            notifications, audit, new ObjectMapper(), runs, sections);
    private final Actor student = new Actor(1L, "student", "STUDENT", null, null);

    @Test
    void forbiddenActionIsRejectedBeforePersistence() {
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> service.prepare(student, "DRAFT", null, Map.of())).status());
        verifyNoInteractions(repository, chat, sectionRepository, notifications, audit, runs, sections);
    }

    @Test
    void proposalFromDifferentAccountIsRejected() {
        when(repository.lock(7L)).thenReturn(new LinkedHashMap<>(Map.of("requested_by", 2L)));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.confirm(student, 7L)).status());
        verifyNoInteractions(notifications, audit, runs, sections);
    }

    @Test
    void changedConversationScopeIsRejected() {
        when(repository.lock(7L)).thenReturn(new LinkedHashMap<>(Map.of(
                "requested_by", 1L, "action", "READ_NOTICES")));
        when(repository.hasDifferentConversationScope(7L, student)).thenReturn(true);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.confirm(student, 7L)).status());
        verifyNoInteractions(notifications, audit, runs, sections);
    }

    @Test
    void expiredProposalCannotChangeData() {
        when(repository.lock(7L)).thenReturn(new LinkedHashMap<>(Map.of(
                "requested_by", 1L, "action", "READ_NOTICES", "expired", true,
                "params_json", "{\"lastId\":12}")));
        assertThrows(ApiFailure.class, () -> service.confirm(student, 7L));
        verifyNoInteractions(notifications, audit, runs, sections);
    }

    @Test
    void alreadyConfirmedProposalReturnsRecordedResultWithoutRepeatingMutation() {
        when(repository.lock(7L)).thenReturn(new LinkedHashMap<>(Map.of(
                "requested_by", 1L, "action", "READ_NOTICES",
                "result_json", "{\"action\":\"READ_NOTICES\",\"marked\":3}")));
        assertEquals(Map.of("action", "READ_NOTICES", "marked", 3), service.confirm(student, 7L));
        verifyNoInteractions(notifications, audit, runs, sections);
        verify(repository, never()).confirm(anyLong(), anyString());
    }

    @Test
    void notificationConfirmationOnlyMarksThroughPreviewedId() {
        when(repository.lock(7L)).thenReturn(new LinkedHashMap<>(Map.of(
                "requested_by", 1L, "action", "READ_NOTICES", "expired", false,
                "params_json", "{\"lastId\":12}")));
        when(notifications.markThrough(1L, 12L)).thenReturn(3);
        assertEquals(Map.of("action", "READ_NOTICES", "marked", 3), service.confirm(student, 7L));
        verify(notifications).markThrough(1L, 12L);
        verify(repository).confirm(eq(7L), anyString());
        verify(audit).record(student, "CONFIRM", "ai_proposals", 7L);
    }

    @Test
    void cancelledProposalCannotBeExecuted() {
        when(repository.lock(7L)).thenReturn(new LinkedHashMap<>(Map.of(
                "requested_by", 1L, "action", "READ_NOTICES", "cancelled_at", "2026-11-27",
                "params_json", "{\"lastId\":12}")));
        assertEquals(409, assertThrows(ApiFailure.class, () -> service.confirm(student, 7L)).status());
        verifyNoInteractions(notifications, audit, runs, sections);
        verify(repository, never()).confirm(anyLong(), anyString());
    }

    @Test
    void noUnreadNotificationsDoesNotCreateProposal() {
        when(notifications.unreadSummary(1L)).thenReturn(new UnreadNotificationSummary(0L, null));
        assertNull(service.prepare(student, "READ_NOTICES", null, Map.of()));
        verifyNoInteractions(repository, audit, runs, sections);
    }

    @Test
    void unreadPreviewPreservesCountAndStoresOnlyThePreviewedLastId() throws Exception {
        when(notifications.unreadSummary(1L)).thenReturn(new UnreadNotificationSummary(3L, 12L));
        when(repository.insert(eq(1L), isNull(), eq("READ_NOTICES"), isNull(), anyString()))
                .thenReturn(new LinkedHashMap<>(Map.of("id", 7L)));
        var result = service.prepare(student, "READ_NOTICES", null, Map.of());
        assertEquals(Map.of("count", 3L), result.get("preview"));
        var stored = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(1L), isNull(), eq("READ_NOTICES"), isNull(), stored.capture());
        var json = new ObjectMapper();
        assertEquals(json.readTree("{\"lastId\":12}"), json.readTree(stored.getValue()));
        verify(notifications, never()).markThrough(anyLong(), anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void applyPreviewCannotUseRunFromAnotherSemester() {
        Actor staff = new Actor(2L, "staff", "STAFF", 3L, 4L);
        when(runs.get(staff, 9L)).thenReturn(Map.of("id", 9L, "semester_id", 8L));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.prepare(staff, "APPLY",
                Map.of("id", 7L), Map.of("runId", 9L))).status());
        verifyNoInteractions(repository, audit, sections, notifications);
    }

    @Test
    void draftPreviewPreservesStoredParametersAndDisplayFields() throws Exception {
        Actor staff = new Actor(2L, "staff", "STAFF", 3L, 4L);
        var preview = new SectionDraftPreview(7L, 5L, "CS01", 30L, 2L, 3L,
                "LECTURE", "2026-09-01", "2026-12-01");
        when(sections.previewDraft(eq(staff), anyMap())).thenReturn(preview);
        when(sectionRepository.findSubject(5L))
                .thenReturn(new SectionSubject(5L, "CS", "Computer Science", "ACTIVE", 4L));
        when(repository.insert(eq(2L), eq(7L), eq("DRAFT"), isNull(), anyString()))
                .thenReturn(new LinkedHashMap<>(Map.of("id", 11L)));
        var result = service.prepare(staff, "DRAFT", Map.of("id", 7L, "code", "HK1"),
                Map.of("draft", Map.of("subjectId", 5L)));
        var stored = ArgumentCaptor.forClass(String.class);
        verify(repository).insert(eq(2L), eq(7L), eq("DRAFT"), isNull(), stored.capture());
        var json = new ObjectMapper();
        var expected = json.readTree("""
                {"semesterId":7,"subjectId":5,"code":"CS01","capacity":30,"sessionsPerWeek":2,
                 "periodsPerSession":3,"requiredRoomType":"LECTURE",
                 "startDate":"2026-09-01","endDate":"2026-12-01"}
                """);
        assertEquals(expected, json.readTree(stored.getValue()));
        var displayed = json.readTree(json.writeValueAsString(result.get("preview")));
        assertEquals("CS · Computer Science", displayed.get("subject").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode) displayed).remove("subject");
        assertEquals(expected, displayed);
        verify(sections, never()).saveDraft(any(), anyMap());
    }

    @Test
    void confirmedDraftKeepsResultContract() {
        Actor staff = new Actor(2L, "staff", "STAFF", 3L, 4L);
        when(repository.lock(11L)).thenReturn(new LinkedHashMap<>(Map.of(
                "requested_by", 2L, "action", "DRAFT", "expired", false,
                "params_json", "{\"code\":\"CS01\"}")));
        when(sections.saveDraft(eq(staff), anyMap())).thenReturn(new SectionIdResponse(9L));
        assertEquals(Map.of("action", "DRAFT", "id", 9L, "code", "CS01"), service.confirm(staff, 11L));
        verify(repository).confirm(eq(11L), anyString());
        verify(audit).record(staff, "CONFIRM", "ai_proposals", 11L);
    }
}
