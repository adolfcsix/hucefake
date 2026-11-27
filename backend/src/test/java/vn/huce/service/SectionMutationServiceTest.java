package vn.huce.service;

import java.time.LocalDate;
import java.util.List;
import vn.huce.model.SectionSemester;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.SchedulePatternRepository;
import vn.huce.repository.SectionRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SectionMutationServiceTest {
    private final SectionRepository repository = mock(SectionRepository.class);
    private final SchedulePatternRepository patterns = mock(SchedulePatternRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final SectionService sections = mock(SectionService.class);
    private final SchedulingService scheduling = mock(SchedulingService.class);
    private final SectionMutationService service = new SectionMutationService(
            repository, patterns, audit, sections, scheduling);
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);

    @Test
    void rejectsDuplicateMainAndAssistantBeforeWriting() {
        when(sections.editable(staff, 7L)).thenReturn(Map.of("version", 1L));
        assertThrows(ApiFailure.class, () -> service.assign(staff, 7L,
                Map.of("staffId", 12L, "assistantIds", List.of(12L))));
        verifyNoInteractions(repository, patterns, audit, scheduling);
    }

    @Test
    void targetGroupsAreDeduplicatedAndScheduleIsRechecked() {
        when(sections.editable(staff, 7L)).thenReturn(Map.of("version", 1L));
        service.groups(staff, 7L, Map.of("groupIds", List.of(2L, 2L, 3L)));
        var ordered = inOrder(repository, scheduling, audit);
        ordered.verify(repository).replaceTargetGroups(7L, java.util.Set.of(2L, 3L));
        ordered.verify(scheduling).recheck(7L, staff);
        ordered.verify(repository).touch(7L);
        ordered.verify(audit).record(staff, "SET_TARGET_GROUPS", "course_sections", 7L);
    }

    @Test
    void invalidImportIsRejectedBeforeCreatingSections() {
        when(repository.findSemester(4L)).thenReturn(new SectionSemester(4L, "HK1", "ACTIVE",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)));
        assertThrows(ApiFailure.class, () -> service.importSections(staff,
                Map.of("semesterId", 4L, "rows", List.of(Map.of("semesterCode", "HK2")))));
        verify(sections, never()).create(any(), any());
        verifyNoInteractions(audit);
    }
}
