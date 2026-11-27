package vn.huce.service;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.SectionDraft;
import vn.huce.model.SectionSemester;
import vn.huce.model.SectionSubject;
import vn.huce.dto.SectionIdResponse;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.SectionRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SectionServiceTest {
    private final SectionRepository repository = mock(SectionRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final SectionService service = new SectionService(repository, audit);
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);
    private final SectionSemester semester = new SectionSemester(4L, "HK1", "ACTIVE",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
    private final SectionSubject subject = new SectionSubject(5L, "CS", "Computer Science", "ACTIVE", 3L);

    private Map<String, Object> input(String start) {
        return Map.of("semesterId", 4L, "subjectId", 5L, "code", " cs01 ",
                "capacity", 30, "sessionsPerWeek", 2, "periodsPerSession", 3,
                "requiredRoomType", "LECTURE", "startDate", start, "endDate", "2026-12-01");
    }

    @Test
    void draftUsesTypedDatesAndNormalizedCode() {
        SectionDraft draft = service.values(semester, subject, input("2026-09-10"));
        assertEquals("CS01", draft.code());
        assertEquals(LocalDate.of(2026, 9, 10), draft.startDate());
        assertEquals(2, draft.sessionsPerWeek());
    }

    @Test
    void rejectsDatesOutsideSemester() {
        assertThrows(ApiFailure.class, () -> service.values(semester, subject, input("2026-08-31")));
    }

    @Test
    void duplicateDraftNeverWrites() {
        when(repository.findSemester(4L)).thenReturn(semester);
        when(repository.findSubject(5L)).thenReturn(subject);
        when(repository.codeExists(4L, "CS01")).thenReturn(true);
        assertEquals("DUPLICATE_REQUEST", assertThrows(ApiFailure.class,
                () -> service.saveDraft(staff, input("2026-09-10"))).code());
        verify(repository, never()).insertDraft(any(), anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void savesAndAuditsDraft() {
        when(repository.findSemester(4L)).thenReturn(semester);
        when(repository.findSubject(5L)).thenReturn(subject);
        when(repository.insertDraft(any(), eq(1L))).thenReturn(9L);
        assertEquals(new SectionIdResponse(9L), service.saveDraft(staff, input("2026-09-10")));
        verify(audit).record(staff, "CREATE", "course_sections", 9L);
    }

    @Test
    void staffCannotCreateDraftForAnotherUnit() {
        when(repository.findSemester(4L)).thenReturn(semester);
        when(repository.findSubject(5L)).thenReturn(new SectionSubject(5L, "CS", "Computer Science", "ACTIVE", 99L));
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> service.saveDraft(staff, input("2026-09-10"))).status());
        verify(repository, never()).insertDraft(any(), anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void staffCannotEditAnotherUnitSection() {
        when(repository.findSection(9L)).thenReturn(Map.of("id", 9L,
                "managing_unit_id", 99L, "status", "DRAFT", "semester_id", 4L));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.editable(staff, 9L)).status());
        verify(repository, never()).hasSubmittedTimetable(anyLong());
        verifyNoInteractions(audit);
    }
}
