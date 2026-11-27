package vn.huce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.dto.ApprovalDecisionResponse;
import vn.huce.dto.TimetableVersionIdResponse;
import vn.huce.model.ApprovalStatus;
import vn.huce.model.TimetableVersionSnapshot;
import vn.huce.model.TimetableVersionStatus;
import vn.huce.repository.ApprovalRepository;
import vn.huce.repository.AuditRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {
    private final ApprovalRepository repository = mock(ApprovalRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final SchedulingService scheduling = mock(SchedulingService.class);
    private final ApprovalService service = new ApprovalService(repository, audit, notifications,
            scheduling, new ObjectMapper());
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);
    private final Actor manager = new Actor(9L, "manager", "MANAGER", null, null);

    @Test
    void pendingSemesterCannotCreateAnotherDraft() {
        when(repository.hasSubmittedVersion(4L)).thenReturn(true);
        assertThrows(ApiFailure.class, () -> service.create(staff, 4L));
        verify(repository, never()).createDraft(anyLong(), anyLong());
    }

    @Test
    void existingDraftReturnsTypedIdWithoutCreatingAnotherVersion() {
        when(repository.findDraft(4L, 1L)).thenReturn(7L);
        assertEquals(new TimetableVersionIdResponse(7L), service.create(staff, 4L));
        verify(repository, never()).createDraft(anyLong(), anyLong());
    }

    @Test
    void newDraftReturnsPersistedVersionId() {
        when(repository.findDraft(4L, 1L)).thenReturn(null);
        when(repository.createDraft(4L, 1L)).thenReturn(8L);
        assertEquals(new TimetableVersionIdResponse(8L), service.create(staff, 4L));
        var order = inOrder(repository);
        order.verify(repository).requireSemester(4L);
        order.verify(repository).hasSubmittedVersion(4L);
        order.verify(repository).findDraft(4L, 1L);
        order.verify(repository).createDraft(4L, 1L);
    }

    @Test
    void submissionChecksOwnerBeforeStatusAndDoesNotWrite() {
        when(repository.findVersion(7L)).thenReturn(new TimetableVersionSnapshot(
                7L, 4L, 1, TimetableVersionStatus.PUBLISHED, 2, 99L));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.submit(staff, 7L)).status());
        verify(repository).findVersion(7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(audit, notifications, scheduling);
    }

    @Test
    void submissionRejectsNonDraftVersionBeforeAssessing() {
        when(repository.findVersion(7L)).thenReturn(new TimetableVersionSnapshot(
                7L, 4L, 1, TimetableVersionStatus.SUBMITTED, 2, 1L));
        var error = assertThrows(ApiFailure.class, () -> service.submit(staff, 7L));
        assertEquals(409, error.status());
        assertEquals("Hồ sơ đang ở trạng thái SUBMITTED.", error.getMessage());
        verify(repository).findVersion(7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(audit, notifications, scheduling);
    }

    @Test
    void publicationRequiresSubmittedVersionBeforeScheduling() {
        when(repository.priorExecution(7L)).thenReturn(null);
        when(repository.findVersionForApproval(7L)).thenReturn(new TimetableVersionSnapshot(
                4L, 2L, 1, TimetableVersionStatus.RETURNED, 2, 1L));
        var error = assertThrows(ApiFailure.class, () -> service.apply(manager, Map.of(
                "id", 7L, "requested_by", 1L, "status", "APPROVED")));
        assertEquals(409, error.status());
        assertEquals("Hồ sơ đang ở trạng thái RETURNED.", error.getMessage());
        verify(repository).priorExecution(7L);
        verify(repository).findVersionForApproval(7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(audit, notifications, scheduling);
    }

    @Test
    void validSubmissionUsesPersistedIdsAndKeepsSnapshotContract() throws Exception {
        var section = Map.<String, Object>of("id", 21L, "semester_id", 4L, "status", "DRAFT",
                "managing_unit_id", 3L, "staff_id", 2L, "sessions_per_week", 1,
                "code", "HP01", "subject_name", "Toán");
        var slot = new SchedulingService.Slot(11L, 21L, 2L, 5L, 1, 2, 3,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        var context = new SchedulingService.Context(Map.of(21L, section),
                Map.of(5L, Map.of("code", "P101")), List.of(), List.of(), List.of(), List.of(slot));
        when(scheduling.context()).thenReturn(context);
        when(scheduling.check(context, slot, staff, false))
                .thenReturn(new SchedulingService.Result(true, List.of(), List.of()));
        when(repository.findVersion(7L)).thenReturn(new TimetableVersionSnapshot(
                7L, 4L, 1, TimetableVersionStatus.DRAFT, 0, 1L));
        when(repository.patternVersion(11L)).thenReturn(2);
        when(repository.createWorkItem(1L)).thenReturn(8L);
        when(repository.createApproval(eq(8L), eq(1L), anyString())).thenReturn(9L);
        assertEquals(9L, service.submit(staff, 7L).approvalId());
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        var order = inOrder(repository, audit, notifications);
        order.verify(repository).createWorkItem(1L);
        order.verify(repository).createApproval(eq(8L), eq(1L), captured.capture());
        order.verify(repository).submitVersion(7L, 1L, 9L);
        order.verify(audit).record(staff, "SUBMIT", "timetable_versions", 7L);
        order.verify(notifications).notifyManagers(anyString());
        var json = new ObjectMapper();
        assertEquals(json.readTree("""
                [{"id":11,"section":21,"staff":2,"room":5,"day":1,"first":2,"count":3,
                "from":"2026-09-01","to":"2026-12-31","version":2,
                "sectionCode":"HP01","subjectName":"Toán","roomCode":"P101"}]
                """), json.readTree(captured.getValue()));
    }

    @Test
    void pendingReadinessKeepsLegacyResponse() {
        when(repository.hasSubmittedVersion(4L)).thenReturn(true);
        assertEquals(Map.of("semesterId", 4L, "pending", true, "ready", false,
                "blockedSections", List.of()), service.readiness(staff, 4L));
        verifyNoInteractions(scheduling);
    }

    @Test
    void emptySemesterReadinessKeepsLegacyResponse() {
        when(scheduling.context()).thenReturn(new SchedulingService.Context(
                Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of()));
        assertEquals(Map.of("semesterId", 4L, "draftCount", 0, "eligible", false,
                "ready", false, "blockedSections", List.of()), service.readiness(staff, 4L));
    }

    @Test
    void conflictDetailsKeepNullRoomAndExistingPatternIdWithoutMutation() throws Exception {
        var section = Map.<String, Object>of("id", 21L, "semester_id", 4L, "status", "DRAFT",
                "managing_unit_id", 3L, "staff_id", 2L, "sessions_per_week", 1,
                "code", "HP01", "subject_name", "Toán");
        var slot = new SchedulingService.Slot(11L, 21L, 2L, 5L, 1, 2, 3,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        var issue = Map.<String, Object>of("rule", "BR05", "message", "Xung đột", "patternId", 99L);
        var context = new SchedulingService.Context(Map.of(21L, section), Map.of(),
                List.of(), List.of(), List.of(), List.of(slot));
        when(scheduling.context()).thenReturn(context);
        when(scheduling.check(context, slot, staff, false))
                .thenReturn(new SchedulingService.Result(false, List.of(issue), List.of()));
        when(repository.findVersion(7L)).thenReturn(new TimetableVersionSnapshot(
                7L, 4L, 1, TimetableVersionStatus.DRAFT, 0, 1L));
        var error = assertThrows(ApiFailure.class, () -> service.submit(staff, 7L));
        assertEquals(409, error.status());
        assertEquals("SCHEDULE_CONFLICT", error.code());
        var json = new ObjectMapper();
        var details = json.readTree(json.writeValueAsString(error.details()));
        assertEquals(json.readTree("""
                {"semesterId":4,"draftCount":1,"eligible":true,"ready":false,
                 "blockedSections":[{"id":21,"code":"HP01","subjectName":"Toán","editable":true,
                 "scheduled":1,"required":1,"missing":[],"conflicts":[{"id":11,"dayOfWeek":1,
                 "periodFrom":2,"roomCode":null,"issues":[{"rule":"BR05","message":"Xung đột","patternId":99}]}]}],
                 "issues":[{"rule":"BR05","message":"Xung đột","patternId":99,"candidateSectionCode":"HP01"}]}
                """), details);
        verify(repository, never()).createWorkItem(anyLong());
        verifyNoInteractions(audit, notifications);
    }

    @Test
    void staffCannotApproveEvenWhenCallingServiceDirectly() {
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> service.decide(staff, Map.of(), Map.of())).status());
        verifyNoInteractions(repository, audit, notifications, scheduling);
    }

    @Test
    void returningApprovalUpdatesWorkflowAndNotifiesRequester() {
        Map<String, Object> approval = Map.of("id", 7L, "requested_by", 1L,
                "status", "PENDING", "work_item_id", 8L);
        assertEquals(new ApprovalDecisionResponse(ApprovalStatus.RETURNED), service.decide(manager, approval,
                Map.of("decision", "RETURNED", "reason", "Kiểm tra lại lịch")));
        var order = inOrder(repository, notifications, audit);
        order.verify(repository).recordDecision(7L, 9L, "RETURNED", "Kiểm tra lại lịch");
        order.verify(repository).changeWorkStatus(8L, "RETURNED");
        order.verify(repository).returnVersion(7L);
        order.verify(notifications).notifyUser(1L, "Thời khóa biểu được trả lại. Lý do: Kiểm tra lại lịch");
        order.verify(audit).record(manager, "RETURNED", "approval_requests", 7L);
    }

    @Test
    void repeatedPublicationReturnsPriorExecutionWithoutWritingAgain() {
        when(repository.priorExecution(7L)).thenReturn("{\"versionId\":4,\"sessions\":12}");
        assertEquals(Map.of("versionId", 4, "sessions", 12),
                service.apply(manager, Map.of("id", 7L, "requested_by", 1L, "status", "APPLIED")));
        verify(repository).priorExecution(7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(audit, notifications, scheduling);
    }

    @Test
    void invalidDecisionDoesNotChangeWorkflow() {
        Map<String, Object> approval = Map.of("id", 7L, "requested_by", 1L,
                "status", "PENDING", "work_item_id", 8L);
        assertEquals(400, assertThrows(ApiFailure.class, () -> service.decide(manager, approval,
                Map.of("decision", "APPLIED"))).status());
        verifyNoInteractions(repository, notifications, audit, scheduling);
    }

    @Test
    void returnedDecisionRequiresNonBlankReason() {
        Map<String, Object> approval = Map.of("id", 7L, "requested_by", 1L,
                "status", "PENDING", "work_item_id", 8L);
        assertEquals(400, assertThrows(ApiFailure.class, () -> service.decide(manager, approval,
                Map.of("decision", "RETURNED", "reason", "  "))).status());
        verifyNoInteractions(repository, notifications, audit, scheduling);
    }

    @Test
    void appliedApprovalCannotBeDecidedAgain() {
        Map<String, Object> approval = Map.of("id", 7L, "requested_by", 1L,
                "status", "APPLIED", "work_item_id", 8L);
        assertEquals(409, assertThrows(ApiFailure.class, () -> service.decide(manager, approval,
                Map.of("decision", "APPROVED"))).status());
        verifyNoInteractions(repository, notifications, audit, scheduling);
    }

    @Test
    void managerCannotApproveOwnRequest() {
        Map<String, Object> approval = Map.of("id", 7L, "requested_by", 9L,
                "status", "PENDING", "work_item_id", 8L);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.decide(manager, approval,
                Map.of("decision", "APPROVED"))).status());
        verifyNoInteractions(repository, notifications, audit, scheduling);
    }
}
