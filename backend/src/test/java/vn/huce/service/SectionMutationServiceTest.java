package vn.huce.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.dto.SchedulePatternIdResponse;
import vn.huce.exception.ApiFailure;
import vn.huce.model.AssignmentRole;
import vn.huce.model.PatternDraft;
import vn.huce.model.SchedulePatternSnapshot;
import vn.huce.model.SectionSemester;
import vn.huce.model.SectionEditSnapshot;
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

    private SectionEditSnapshot section(long version) {
        return new SectionEditSnapshot(7L, 4L, 3L, "DRAFT", version, 0L, 2, 3, 12L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1),
                Map.of("version", version, "code", "HP07"));
    }

    @Test
    void rejectsDuplicateMainAndAssistantBeforeWriting() {
        when(sections.editable(staff, 7L)).thenReturn(section(1L));
        assertThrows(ApiFailure.class, () -> service.assign(staff, 7L,
                Map.of("staffId", 12L, "assistantIds", List.of(12L))));
        verifyNoInteractions(repository, patterns, audit, scheduling);
    }

    @Test
    void targetGroupsAreDeduplicatedAndScheduleIsRechecked() {
        when(sections.editable(staff, 7L)).thenReturn(section(1L));
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

    @Test
    void staleVersionIsRejectedBeforeAssignmentParsing() {
        when(sections.editable(staff, 7L)).thenReturn(section(2L));
        var error = assertThrows(ApiFailure.class, () ->
                service.assign(staff, 7L, Map.of("version", 1L, "staffId", false)));
        assertEquals(409, error.status());
        assertEquals("VERSION_CONFLICT", error.code());
        assertEquals(section(2L).conflictDetails(), error.details());
        verifyNoInteractions(repository, patterns, audit, scheduling);
    }

    @Test
    void invalidGroupSelectionDoesNotMutateRepository() {
        when(sections.editable(staff, 7L)).thenReturn(section(1L));
        var error = assertThrows(ApiFailure.class, () ->
                service.groups(staff, 7L, Map.of("groupIds", List.of(1, 1.5))));
        assertEquals(400, error.status());
        verifyNoInteractions(repository, patterns, audit, scheduling);
    }

    @Test
    void assignmentRetainsMainAndReplacesOnlyChangedAssistants() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        when(sections.editable(staff, 7L)).thenReturn(section(1L));
        when(repository.isActiveLecturer(12L)).thenReturn(true);
        when(repository.isActiveLecturer(13L)).thenReturn(true);
        when(repository.activeAssignments(7L)).thenReturn(List.of(
                new SectionRepository.Assignment(21L, 12L, AssignmentRole.MAIN),
                new SectionRepository.Assignment(22L, 14L, AssignmentRole.ASSISTANT)));
        service.assign(staff, 7L, Map.of("staffId", 12L, "assistantIds", List.of(13L)));
        verify(repository, never()).closeAssignment(21L);
        var ordered = inOrder(repository, scheduling, audit);
        ordered.verify(repository).closeAssignment(22L);
        ordered.verify(repository).addAssignment(7L, 13L, AssignmentRole.ASSISTANT, start, 1L);
        ordered.verify(scheduling).recheck(7L, staff);
        ordered.verify(repository).touch(7L);
        ordered.verify(audit).record(staff, "ASSIGN", "course_sections", 7L);
        verify(repository, never()).replacePatternLecturer(anyLong(), anyLong());
    }

    @Test
    void schedulePatternWriteReturnsTypedIdAndPreservesAudit() {
        var section = section(1L);
        var input = Map.<String, Object>of("sectionId", 7L);
        var context = new SchedulingService.Context(Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of());
        var from = LocalDate.of(2026, 9, 1);
        var to = LocalDate.of(2026, 12, 1);
        var slot = new SchedulingService.Slot(0L, 7L, 12L, 4L, 2, 1, 3, from, to);
        var draft = new PatternDraft(7L, 12L, 4L, 2, 1, 3, from, to);
        when(sections.editable(staff, 7L)).thenReturn(section);
        when(scheduling.context()).thenReturn(context);
        when(scheduling.candidate(staff, context, input, null, section)).thenReturn(slot);
        when(scheduling.check(context, slot, staff, true))
                .thenReturn(new SchedulingService.Result(true, List.of(), List.of()));
        when(patterns.insert(draft, 1L)).thenReturn(42L);
        assertEquals(new SchedulePatternIdResponse(42L), service.savePattern(staff, input, null));
        verify(audit).record(staff, "SAVE", "section_schedule_patterns", 42L);
    }

    @Test
    void stalePatternDeletionPreservesDetailsAndDoesNotWrite() {
        var pattern = new SchedulePatternSnapshot(42L, 7L, 12L, 4L, 2, 1, 3,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1), "MANUAL",
                null, 1L, LocalDateTime.of(2026, 9, 1, 8, 0), 2, 5L);
        when(patterns.findWithCampus(42L)).thenReturn(pattern);
        var error = assertThrows(ApiFailure.class, () -> service.deletePattern(staff, 42L, "1"));
        assertEquals(409, error.status());
        assertEquals("VERSION_CONFLICT", error.code());
        assertEquals(pattern.conflictDetails(), error.details());
        var ordered = inOrder(patterns, sections, scheduling);
        ordered.verify(patterns).findWithCampus(42L);
        ordered.verify(sections).editable(staff, 7L);
        ordered.verify(scheduling).requireCampus(staff, 5L);
        verify(patterns, never()).delete(anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void matchingPatternVersionDeletesAndAudits() {
        var pattern = new SchedulePatternSnapshot(42L, 7L, 12L, 4L, 2, 1, 3,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1), "MANUAL",
                null, 1L, LocalDateTime.of(2026, 9, 1, 8, 0), 2, 5L);
        when(patterns.findWithCampus(42L)).thenReturn(pattern);
        assertNull(service.deletePattern(staff, 42L, "2"));
        var ordered = inOrder(patterns, sections, scheduling, audit);
        ordered.verify(patterns).findWithCampus(42L);
        ordered.verify(sections).editable(staff, 7L);
        ordered.verify(scheduling).requireCampus(staff, 5L);
        ordered.verify(patterns).delete(42L);
        ordered.verify(audit).record(staff, "DELETE", "section_schedule_patterns", 42L);
    }
}
