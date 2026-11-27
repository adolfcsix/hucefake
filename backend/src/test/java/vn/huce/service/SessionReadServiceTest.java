package vn.huce.service;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.ScheduleFilter;
import vn.huce.repository.NotificationRepository;
import vn.huce.repository.SessionReadRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionReadServiceTest {
    private final SessionReadRepository repository = mock(SessionReadRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final SessionReadService service = new SessionReadService(repository, notifications);
    private final Actor student = new Actor(1L, "student", "STUDENT", null, null);
    private final Actor lecturer = new Actor(2L, "lecturer", "LECTURER", 3L, 4L);

    @Test
    void studentCannotReadAllSchedules() {
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.schedule(student,
                Map.of("semesterId", "5", "view", "all"))).status());
        verifyNoInteractions(repository);
    }

    @Test
    void invalidDateRangeIsRejectedBeforeQuery() {
        assertThrows(ApiFailure.class, () -> service.schedule(student,
                Map.of("semesterId", "5", "from", "2026-12-05", "to", "2026-12-01")));
        verifyNoInteractions(repository);
    }

    @Test
    void nextSessionUsesSameActorScopeAndTypedDateFilter() {
        Map<String, String> query = Map.of("semesterId", "5", "from", "2026-12-01",
                "to", "2026-12-07", "page", "1", "pageSize", "1");
        service.nextSession(lecturer, query);
        verify(repository).schedule(lecturer, new ScheduleFilter(5L, LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 7), ScheduleFilter.View.PERSONAL, null), query, true);
    }

    @Test
    void notificationReadsAreScopedToCurrentUser() {
        service.markRead(student, 7L);
        service.markAll(student);
        verify(notifications).markRead(1L, 7L);
        verify(notifications).markAll(1L);
    }
}
