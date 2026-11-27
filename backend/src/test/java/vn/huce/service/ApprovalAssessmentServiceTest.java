package vn.huce.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.model.SchedulingSection;
import vn.huce.repository.ApprovalRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApprovalAssessmentServiceTest {
    private final ApprovalRepository repository = mock(ApprovalRepository.class);
    private final SchedulingService scheduling = mock(SchedulingService.class);
    private final ApprovalAssessmentService service = new ApprovalAssessmentService(repository, scheduling);
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);

    private SchedulingSection section(long id, long semesterId, long unitId, String status, Long lecturer) {
        return new SchedulingSection(id, semesterId, unitId, status, "HP" + id, "Toán", lecturer,
                30, "LECTURE", 1, 2, 0L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                Set.of(), Map.of());
    }

    @Test
    void assessmentOnlyCountsDraftsInSelectedSemesterAndRequiresOwnUnit() {
        when(scheduling.context()).thenReturn(new SchedulingService.Context(
                Map.of(1L, section(1L, 4L, 8L, "DRAFT", 2L),
                        2L, section(2L, 5L, 3L, "DRAFT", 2L),
                        3L, section(3L, 4L, 3L, "OPEN", 2L)),
                Map.of(), List.of(), List.of(), List.of(), List.of()));
        var result = service.assess(staff, 4L);
        assertEquals(1, result.readiness().draftCount());
        assertFalse(result.readiness().eligible());
        assertFalse(result.readiness().ready());
        assertTrue(result.snapshot().isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void unassignedAndUnscheduledDraftReportsBothMissingRequirements() {
        when(scheduling.context()).thenReturn(new SchedulingService.Context(
                Map.of(1L, section(1L, 4L, 3L, "DRAFT", null)),
                Map.of(), List.of(), List.of(), List.of(), List.of()));
        var result = service.assess(staff, 4L);
        assertTrue(result.readiness().eligible());
        assertFalse(result.readiness().ready());
        assertEquals(List.of("BR02", "BR10"), result.readiness().blockedSections().getFirst()
                .missing().stream().map(issue -> issue.get("rule")).toList());
        assertTrue(result.snapshot().isEmpty());
        verifyNoInteractions(repository);
    }
}
