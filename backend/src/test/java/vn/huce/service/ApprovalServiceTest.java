package vn.huce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
        when(repository.priorExecution(7L)).thenReturn(Map.of("result", "{\"versionId\":4,\"sessions\":12}"));
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
