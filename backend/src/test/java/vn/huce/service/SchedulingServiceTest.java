package vn.huce.service;

import vn.huce.model.SchedulingSection;
import vn.huce.model.SectionEditSnapshot;
import vn.huce.model.SchedulingRoom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.SchedulePatternSnapshot;
import vn.huce.model.NonTeachingDay;
import vn.huce.repository.SchedulingRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static vn.huce.service.SchedulingService.*;

class SchedulingServiceTest {
    private final SchedulingRepository repository = mock(SchedulingRepository.class);
    private final SchedulingService service = new SchedulingService(repository);

    @Test
    void contextPreservesTypedPatternCoordinatesAndOrder() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = from.plusWeeks(4);
        when(repository.draftPatterns()).thenReturn(List.of(
                new SchedulePatternSnapshot(11L, 2L, 3L, 4L, 1, 2, 3, from, to,
                        "MANUAL", null, 9L, from.atStartOfDay(), 1, null),
                new SchedulePatternSnapshot(12L, 5L, 6L, 7L, 3, 4, 2, from, to,
                        "GENETIC", 8L, 9L, from.atStartOfDay(), 2, null)));
        assertEquals(List.of(new Slot(11L, 2L, 3L, 4L, 1, 2, 3, from, to),
                new Slot(12L, 5L, 6L, 7L, 3, 4, 2, from, to)), service.context().patterns());
    }

    @Test
    void actualConflictQueryIncludesAssistantsAndExcludesOriginalSession() {
        LocalDate date = LocalDate.of(2026, 11, 30);
        Slot slot = new Slot(1L, 2L, 3L, 4L, 1, 1, 2, date, date);
        Context context = new Context(Map.of(2L, new SchedulingSection(2L, 1L, 1L, "DRAFT", "HP02", "Toán", 3L,
                30, "LECTURE", 1, 2, 0L, date, date, Set.of(6L, 7L), Map.of())),
                Map.of(), List.of(), List.of(), List.of(), List.of());
        Span span = new Span(date.atTime(7, 0), date.atTime(8, 50));
        service.actualConflicts(context, slot, span, 9L);
        verify(repository).actualConflicts(2L, 4L, Set.of(3L, 6L, 7L), span.start(), span.end(), 9L);
    }

    @Test
    void unauthorizedCampusIsRejected() {
        Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);
        when(repository.allowedCampuses(staff)).thenReturn(Set.of(5L));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.requireCampus(staff, 6L)).status());
    }

    private final LocalDate monday = LocalDate.of(2026, 11, 30);
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);

    private SectionEditSnapshot section() {
        return new SectionEditSnapshot(2L, 1L, 1L, "DRAFT", 0L, 0L, 1, 2, 3L,
                monday, monday.plusWeeks(2), Map.of());
    }

    private Context candidateContext(List<Slot> patterns) {
        return new Context(Map.of(2L, new SchedulingSection(2L, 1L, 1L, "DRAFT", "HP02", "Toán", 3L,
                30, "LECTURE", 1, 2, 0L, monday, monday.plusWeeks(2), Set.of(), Map.of())),
                Map.of(4L, new SchedulingRoom(4L, 5L, "P4", 40, "LECTURE", "ACTIVE"),
                        6L, new SchedulingRoom(6L, 7L, "P6", 40, "LECTURE", "ACTIVE")),
                List.of(), List.of(), List.of(), patterns);
    }

    @Test
    void newCandidateDefaultsToSectionDates() {
        when(repository.allowedCampuses(staff)).thenReturn(Set.of(5L));
        Slot slot = service.candidate(staff, candidateContext(List.of()),
                Map.of("roomId", 4L, "dayOfWeek", 1, "periodFrom", 2, "effectiveFrom", ""),
                null, section());
        assertEquals(new Slot(0L, 2L, 3L, 4L, 1, 2, 2, monday, monday.plusWeeks(2)), slot);
    }

    @Test
    void movingPatternRequiresAccessToBothCampuses() {
        when(repository.allowedCampuses(staff)).thenReturn(Set.of(5L));
        Slot prior = new Slot(9L, 2L, 3L, 6L, 1, 1, 2, monday, monday);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.candidate(staff,
                candidateContext(List.of(prior)), Map.of("roomId", 4L), 9L, section())).status());
    }

    @Test
    void existingPatternCannotBeMovedToAnotherSection() {
        Slot prior = new Slot(9L, 8L, 3L, 4L, 1, 1, 2, monday, monday);
        assertEquals(400, assertThrows(ApiFailure.class, () -> service.candidate(staff,
                candidateContext(List.of(prior)), Map.of("roomId", 4L), 9L, section())).status());
        verifyNoInteractions(repository);
    }

    @Test
    void allHolidayOccurrencesReturnBr14WithoutQueryingPublishedSessions() {
        Context context = new Context(Map.of(2L, new SchedulingSection(2L, 1L, 1L, "DRAFT", "HP02", "Toán", 3L,
                30, "LECTURE", 1, 2, 0L, monday, monday.plusWeeks(2), Set.of(), Map.of())), Map.of(4L, new SchedulingRoom(4L, 5L, "P4", 40, "LECTURE", "ACTIVE")),
                List.of(), List.of(), List.of(new NonTeachingDay(5L, monday, monday.plusWeeks(2))), List.of());
        Slot slot = new Slot(1L, 2L, 3L, 4L, 1, 1, 2, monday, monday.plusWeeks(2));
        assertEquals("BR14", service.actual(context, slot).getFirst().get("rule"));
        verifyNoInteractions(repository);
    }
}
