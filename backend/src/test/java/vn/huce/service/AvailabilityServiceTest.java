package vn.huce.service;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.AvailabilityEntry;
import vn.huce.repository.ApprovalRepository;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.AvailabilityRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvailabilityServiceTest {
    private final AvailabilityRepository repository = mock(AvailabilityRepository.class);
    private final ApprovalRepository approvals = mock(ApprovalRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final SchedulingService scheduling = mock(SchedulingService.class);
    private final AvailabilityService service = new AvailabilityService(repository, approvals, audit, scheduling);
    private final Actor lecturer = new Actor(1L, "lecturer", "LECTURER", 2L, 3L);

    @Test
    void rejectsInvalidEntryBeforeReplacingExistingAvailability() {
        Map<String, Object> entry = Map.of("dayOfWeek", 2, "periodFrom", 5, "periodTo", 3, "kind", "BUSY");
        assertThrows(ApiFailure.class, () -> service.replace(lecturer,
                Map.of("semesterId", 4L, "entries", List.of(entry))));
        verifyNoInteractions(repository, audit, scheduling);
    }

    @Test
    void pendingApprovalPreventsAvailabilityChanges() {
        when(approvals.hasSubmittedVersion(4L)).thenReturn(true);
        assertThrows(ApiFailure.class, () -> service.replace(lecturer,
                Map.of("semesterId", 4L, "entries", List.of())));
        verifyNoInteractions(repository, audit, scheduling);
    }

    @Test
    void studentCannotReadOrReplaceLecturerAvailability() {
        Actor student = new Actor(5L, "student", "STUDENT", null, null);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.find(student, 4L)).status());
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.replace(student, Map.of())).status());
        verifyNoInteractions(repository, approvals, audit, scheduling);
    }

    @Test
    void validEntriesAreTypedAndAudited() {
        when(scheduling.context()).thenReturn(new SchedulingService.Context(
                Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of()));
        Map<String, Object> entry = Map.of("dayOfWeek", 2, "periodFrom", 3, "periodTo", 5, "kind", "AVOID");
        service.replace(lecturer, Map.of("semesterId", 4L, "entries", List.of(entry)));
        verify(repository).replace(2L, 4L, List.of(new AvailabilityEntry(2, 3, 5,
                AvailabilityEntry.Kind.AVOID, null)));
        verify(audit).record(lecturer, "SET_AVAILABILITY", "semesters", 4L);
    }
}
