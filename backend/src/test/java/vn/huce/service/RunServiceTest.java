package vn.huce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import vn.huce.dto.ScheduleRunIdResponse;
import vn.huce.dto.ScheduleRunApplyResponse;
import vn.huce.model.ScheduleRunItem;
import vn.huce.model.ScheduleRunStatus;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.ApprovalRepository;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.RunRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunServiceTest {
    private final RunRepository repository = mock(RunRepository.class);
    private final ApprovalRepository approvals = mock(ApprovalRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final SchedulingService scheduling = mock(SchedulingService.class);
    private final GeneticScheduler genetic = mock(GeneticScheduler.class);
    private final RunService service = new RunService(repository, approvals, audit,
            scheduling, genetic, new ObjectMapper());
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);

    private Map<String, Object> run(long requester, String status, int hardViolations) {
        return new LinkedHashMap<>(Map.of("id", 7L, "semester_id", 4L,
                "requested_by", requester, "status", status,
                "hard_violations", hardViolations, "parameters_json", "{}"));
    }

    @Test
    void studentCannotAccessRuns() {
        Actor student = new Actor(9L, "student", "STUDENT", null, null);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.get(student, 7L)).status());
        verifyNoInteractions(repository, approvals, audit, scheduling, genetic);
    }

    @Test
    void otherUsersQueuedRunIsNotVisibleToStaff() {
        when(repository.find(7L)).thenReturn(run(2L, "QUEUED", 0));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.get(staff, 7L)).status());
        verify(repository, never()).visibleManagerRun(anyLong(), any());
    }

    @Test
    void managerRunOutsideStaffUnitIsRejected() {
        when(repository.find(7L)).thenReturn(run(2L, "COMPLETED", 0));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.get(staff, 7L)).status());
        verify(repository).visibleManagerRun(7L, 3L);
    }

    @Test
    void pendingApprovalPreventsQueueing() {
        when(approvals.hasSubmittedVersion(4L)).thenReturn(true);
        assertThrows(ApiFailure.class, () -> service.enqueue(staff, 4L, Map.of()));
        verifyNoInteractions(repository, audit, genetic, scheduling);
    }

    @Test
    void activeRunPreventsDuplicateQueueing() {
        when(repository.active(4L)).thenReturn(true);
        assertEquals("DUPLICATE_REQUEST", assertThrows(ApiFailure.class,
                () -> service.enqueue(staff, 4L, Map.of())).code());
        verify(repository, never()).enqueue(anyLong(), anyLong(), anyString());
        verifyNoInteractions(audit, genetic, scheduling);
    }

    @Test
    void enqueueReturnsTypedIdAndAuditsThePersistedRun() {
        when(repository.enqueue(eq(4L), eq(1L), anyString())).thenReturn(7L);
        assertEquals(new ScheduleRunIdResponse(7L), service.enqueue(staff, 4L, Map.of("seed", 10)));
        var order = inOrder(genetic, repository, audit);
        order.verify(genetic).input(staff, 4L);
        order.verify(repository).enqueue(eq(4L), eq(1L), anyString());
        order.verify(audit).record(staff, "QUEUE", "schedule_runs", 7L);
    }

    @Test
    void hardViolationsPreventApplyingRun() {
        when(repository.find(7L)).thenReturn(run(1L, "COMPLETED", 2));
        assertThrows(ApiFailure.class, () -> service.apply(staff, 7L));
        verify(repository, never()).markApplied(anyLong());
        verifyNoInteractions(approvals, scheduling, genetic, audit);
    }

    @Test
    void repeatedApplyDoesNotWriteAgain() {
        when(repository.find(7L)).thenReturn(run(1L, "APPLIED", 0));
        assertEquals(new ScheduleRunApplyResponse(ScheduleRunStatus.APPLIED, null), service.apply(staff, 7L));
        verify(repository).find(7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(approvals, scheduling, genetic, audit);
    }

    @Test
    void runningJobCannotBeDiscarded() {
        when(repository.find(7L)).thenReturn(run(1L, "RUNNING", 0));
        assertThrows(ApiFailure.class, () -> service.discard(staff, 7L));
        verify(repository, never()).discard(anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void completedManagerRunCanBeReadWithinStaffUnit() {
        when(repository.find(7L)).thenReturn(run(2L, "COMPLETED", 0));
        when(repository.visibleManagerRun(7L, 3L)).thenReturn(true);
        var response = service.get(staff, 7L);
        assertEquals("COMPLETED", response.get("status"));
        assertEquals(Map.of(), response.get("parameters"));
        assertFalse(response.containsKey("parameters_json"));
    }

    @Test
    void failedOwnedRunCanBeDiscardedAndAudited() {
        when(repository.find(7L)).thenReturn(run(1L, "FAILED", 0));
        service.discard(staff, 7L);
        verify(repository).discard(7L);
        verify(audit).record(staff, "DISCARD", "schedule_runs", 7L);
    }

    @Test
    void staleTypedPlacementCannotBeApplied() {
        when(repository.find(7L)).thenReturn(run(1L, "COMPLETED", 0));
        var context = new SchedulingService.Context(Map.of(), Map.of(),
                List.of(), List.of(), List.of(), List.of());
        when(scheduling.context()).thenReturn(context);
        when(genetic.targets(context, staff, 4L)).thenReturn(List.of());
        when(repository.rawItems(7L)).thenReturn(List.of(new ScheduleRunItem(10L, 2L, 3L, 1, 1, 2)));
        assertEquals("VERSION_CONFLICT", assertThrows(ApiFailure.class,
                () -> service.apply(staff, 7L)).code());
        verify(repository, never()).markApplied(anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void statusRulesMatchExistingLifecycle() {
        for (ScheduleRunStatus status : ScheduleRunStatus.values()) {
            assertEquals(java.util.Set.of("COMPLETED", "APPLIED").contains(status.name()),
                    status.visibleToOtherStaff());
            assertEquals(java.util.Set.of("QUEUED", "COMPLETED", "FAILED").contains(status.name()),
                    status.discardable());
        }
    }
}
