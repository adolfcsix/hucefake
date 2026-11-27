package vn.huce.service.chat;

import vn.huce.model.ChatSemester;
import vn.huce.model.ProposalAction;
import vn.huce.model.ProposalSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.dto.SectionDraftPreview;
import vn.huce.dto.SectionIdResponse;
import vn.huce.dto.ScheduleRunIdResponse;
import vn.huce.dto.ScheduleRunApplyResponse;
import vn.huce.model.ScheduleRunStatus;
import vn.huce.model.ScheduleRunSnapshot;
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
        when(repository.lock(7L)).thenReturn(new ProposalSnapshot(2L, null, null, ProposalAction.READ_NOTICES, null, null, false, false));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.confirm(student, 7L)).status());
        verifyNoInteractions(notifications, audit, runs, sections);
    }

    @Test
    void changedConversationScopeIsRejected() {
        when(repository.lock(7L)).thenReturn(new ProposalSnapshot(1L, null, null, ProposalAction.READ_NOTICES, null, null, false, false));
        when(repository.hasDifferentConversationScope(7L, student)).thenReturn(true);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.confirm(student, 7L)).status());
        verifyNoInteractions(notifications, audit, runs, sections);
    }

    @Test
    void expiredProposalCannotChangeData() {
        when(repository.lock(7L)).thenReturn(new ProposalSnapshot(1L, null, null, ProposalAction.READ_NOTICES, "{\"lastId\":12}", null, false, true));
        assertThrows(ApiFailure.class, () -> service.confirm(student, 7L));
        verifyNoInteractions(notifications, audit, runs, sections);
    }

    @Test
    void alreadyConfirmedProposalReturnsRecordedResultWithoutRepeatingMutation() {
        when(repository.lock(7L)).thenReturn(new ProposalSnapshot(1L, null, null, ProposalAction.READ_NOTICES, null, "{\"action\":\"READ_NOTICES\",\"marked\":3}", false, false));
        assertEquals(Map.of("action", "READ_NOTICES", "marked", 3), service.confirm(student, 7L));
        verifyNoInteractions(notifications, audit, runs, sections);
        verify(repository, never()).confirm(anyLong(), anyString());
    }

    @Test
    void notificationConfirmationOnlyMarksThroughPreviewedId() {
        when(repository.lock(7L)).thenReturn(new ProposalSnapshot(1L, null, null, ProposalAction.READ_NOTICES, "{\"lastId\":12}", null, false, false));
        when(notifications.markThrough(1L, 12L)).thenReturn(3);
        assertEquals(Map.of("action", "READ_NOTICES", "marked", 3), service.confirm(student, 7L));
        verify(notifications).markThrough(1L, 12L);
        verify(repository).confirm(eq(7L), anyString());
        verify(audit).record(student, "CONFIRM", "ai_proposals", 7L);
    }

    @Test
    void cancelledProposalCannotBeExecuted() {
        when(repository.lock(7L)).thenReturn(new ProposalSnapshot(1L, null, null, ProposalAction.READ_NOTICES, "{\"lastId\":12}", null, true, false));
        assertEquals(409, assertThrows(ApiFailure.class, () -> service.confirm(student, 7L)).status());
        verifyNoInteractions(notifications, audit, runs, sections);
        verify(repository, never()).confirm(anyLong(), anyString());
    }

    @Test
    void confirmedProposalCannotBeCancelled() {
        when(repository.lock(7L)).thenReturn(new ProposalSnapshot(1L, null, null,
                ProposalAction.READ_NOTICES, null, "{\"marked\":3}", false, false));
        assertEquals(409, assertThrows(ApiFailure.class, () -> service.cancel(student, 7L)).status());
        verify(repository, never()).cancel(anyLong());
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
        when(runs.snapshot(staff, 9L)).thenReturn(new ScheduleRunSnapshot(
                9L, 8L, 2L, ScheduleRunStatus.COMPLETED, 0));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.prepare(staff, "APPLY",
                new ChatSemester(7L, "HK1", java.time.LocalDate.of(2026, 9, 1),
                        java.time.LocalDate.of(2026, 12, 31)), Map.of("runId", 9L))).status());
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
        var result = service.prepare(staff, "DRAFT", new ChatSemester(7L, "HK1",
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 12, 31)),
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
    void confirmedGenerateKeepsResultContract() {
        Actor staff = new Actor(2L, "staff", "STAFF", 3L, 4L);
        when(repository.lock(11L)).thenReturn(new ProposalSnapshot(2L, 7L, null, ProposalAction.GENERATE, "{}", null, false, false));
        when(runs.enqueue(eq(staff), eq(7L), anyMap())).thenReturn(new ScheduleRunIdResponse(9L));
        assertEquals(Map.of("action", "GENERATE", "id", 9L), service.confirm(staff, 11L));
        verify(repository).confirm(eq(11L), anyString());
        verify(audit).record(staff, "CONFIRM", "ai_proposals", 11L);
    }

    @Test
    void confirmedApplyKeepsOptionalPatternCount() {
        Actor staff = new Actor(2L, "staff", "STAFF", 3L, 4L);
        when(repository.lock(11L)).thenAnswer(call -> new ProposalSnapshot(2L, 7L, 9L, ProposalAction.APPLY, null, null, false, false));
        when(runs.snapshot(staff, 9L)).thenReturn(new ScheduleRunSnapshot(
                9L, 7L, 2L, ScheduleRunStatus.COMPLETED, 0));
        when(runs.apply(staff, 9L)).thenReturn(
                new ScheduleRunApplyResponse(ScheduleRunStatus.APPLIED, 3),
                new ScheduleRunApplyResponse(ScheduleRunStatus.APPLIED, null));
        assertEquals(Map.of("action", "APPLY", "status", "APPLIED", "id", 9L, "patterns", 3),
                service.confirm(staff, 11L));
        assertEquals(Map.of("action", "APPLY", "status", "APPLIED", "id", 9L),
                service.confirm(staff, 11L));
    }

    @Test
    void confirmedDraftKeepsResultContract() {
        Actor staff = new Actor(2L, "staff", "STAFF", 3L, 4L);
        when(repository.lock(11L)).thenReturn(new ProposalSnapshot(2L, null, null, ProposalAction.DRAFT, "{\"code\":\"CS01\"}", null, false, false));
        when(sections.saveDraft(eq(staff), anyMap())).thenReturn(new SectionIdResponse(9L));
        assertEquals(Map.of("action", "DRAFT", "id", 9L, "code", "CS01"), service.confirm(staff, 11L));
        verify(repository).confirm(eq(11L), anyString());
        verify(audit).record(staff, "CONFIRM", "ai_proposals", 11L);
    }
}
