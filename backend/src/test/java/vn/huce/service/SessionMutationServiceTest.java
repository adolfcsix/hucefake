package vn.huce.service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.SectionRepository;
import vn.huce.repository.SessionChangeRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionMutationServiceTest {
    private final SessionChangeRepository repository = mock(SessionChangeRepository.class);
    private final SectionRepository sections = mock(SectionRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final SchedulingService scheduling = mock(SchedulingService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final SessionMutationService service = new SessionMutationService(repository, sections, audit,
            scheduling, notifications, availability);
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);

    @Test
    void studentCannotChangeSessionOrDecideRequest() {
        Actor student = new Actor(9L, "student", "STUDENT", null, null);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.change(student, 7L, Map.of(), null)).status());
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.decide(student, 7L, Map.of())).status());
        verifyNoInteractions(repository, sections, audit, scheduling, notifications, availability);
    }

    @Test
    void returnRequestNotifiesLecturerWithoutChangingSession() {
        when(repository.findRequest(7L)).thenReturn(Map.of("managing_unit_id", 3L,
                "status", "PENDING", "staff_id", 8L, "session_id", 9L, "action", "CANCEL"));
        service.decide(staff, 7L, Map.of("action", "RETURNED", "reason", "Giữ lịch hiện tại"));
        verify(repository).decideRequest(7L, "RETURNED", "Giữ lịch hiện tại");
        verify(notifications).notifyStaffProfile(8L, "Đề nghị đổi lịch đã được trả lại. Giữ lịch hiện tại");
        verify(audit).record(staff, "RETURNED", "session_change_requests", 7L);
        verify(repository, never()).cancelSession(anyLong());
    }
}
