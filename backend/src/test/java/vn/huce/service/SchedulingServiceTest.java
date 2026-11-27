package vn.huce.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SchedulingRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static vn.huce.service.SchedulingService.*;

class SchedulingServiceTest {
    private final SchedulingRepository repository = mock(SchedulingRepository.class);
    private final SchedulingService service = new SchedulingService(repository);

    @Test
    void actualConflictQueryIncludesAssistantsAndExcludesOriginalSession() {
        LocalDate date = LocalDate.of(2026, 11, 30);
        Slot slot = new Slot(1L, 2L, 3L, 4L, 1, 1, 2, date, date);
        Context context = new Context(Map.of(2L, Map.of("assistant_ids", new Long[]{6L, 7L})),
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
}
