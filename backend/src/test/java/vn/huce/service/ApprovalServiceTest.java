package vn.huce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
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
    void staffCannotApproveEvenWhenCallingServiceDirectly() {
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> service.decide(staff, Map.of(), Map.of())).status());
        verifyNoInteractions(repository, audit, notifications, scheduling);
    }

    @Test
    void returningApprovalUpdatesWorkflowAndNotifiesRequester() {
        Map<String, Object> approval = Map.of("id", 7L, "requested_by", 1L,
                "status", "PENDING", "work_item_id", 8L);
        assertEquals(Map.of("status", "RETURNED"), service.decide(manager, approval,
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
}
