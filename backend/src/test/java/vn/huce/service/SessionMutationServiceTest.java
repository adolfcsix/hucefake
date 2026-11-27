package vn.huce.service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.SessionRequestStatus;
import vn.huce.model.SessionChangeAction;
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
        verify(repository).decideRequest(7L, SessionRequestStatus.RETURNED, "Giữ lịch hiện tại");
        verify(notifications).notifyStaffProfile(8L, "Đề nghị đổi lịch đã được trả lại. Giữ lịch hiện tại");
        verify(audit).record(staff, "RETURNED", "session_change_requests", 7L);
        verify(repository, never()).cancelSession(anyLong());
    }

    @Test
    void mismatchedDecisionDoesNotAdjustSession() {
        when(repository.findRequest(7L)).thenReturn(Map.of("managing_unit_id", 3L,
                "status", "PENDING", "staff_id", 8L, "session_id", 9L, "action", "CANCEL"));
        assertEquals(400, assertThrows(ApiFailure.class, () -> service.decide(staff, 7L,
                Map.of("action", "RESCHEDULE", "reason", "Đổi lịch"))).status());
        verify(repository, never()).findSession(anyLong());
        verify(repository, never()).decideRequest(anyLong(), any(), anyString());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void processedRequestCannotBeDecidedAgain() {
        when(repository.findRequest(7L)).thenReturn(Map.of("managing_unit_id", 3L,
                "status", "APPROVED"));
        assertEquals(409, assertThrows(ApiFailure.class, () -> service.decide(staff, 7L,
                Map.of("action", "RETURNED", "reason", "Kiểm tra lại"))).status());
        verify(repository, never()).decideRequest(anyLong(), any(), anyString());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void lecturerCannotRequestRoomChange() {
        Actor lecturer = new Actor(5L, "lecturer", "LECTURER", 8L, 3L);
        when(repository.findSessionWithStaff(9L)).thenReturn(Map.of("staff_ids", new Long[]{8L},
                "section_id", 10L, "status", "PLANNED"));
        when(sections.findSection(10L)).thenReturn(Map.of("status", "OPEN"));
        assertEquals(400, assertThrows(ApiFailure.class, () -> service.request(lecturer, 9L,
                Map.of("action", "ROOM_CHANGE", "reason", "Đổi phòng"))).status());
        verify(repository, never()).insertRequest(anyLong(), anyLong(), any(), anyString());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void directCancellationKeepsNullReplacementAndClosesPendingRequests() {
        when(repository.findSession(9L)).thenReturn(Map.of("section_id", 10L, "status", "PLANNED",
                "start_at", "2026-11-30T07:00:00"));
        when(sections.findSection(10L)).thenReturn(Map.of("id", 10L, "status", "OPEN",
                "managing_unit_id", 3L, "code", "HP01"));
        when(repository.insertException(eq(9L), any(), eq(SessionChangeAction.CANCEL),
                isNull(), eq("Nghỉ học"), eq(1L))).thenReturn(Map.of("id", 11L));
        when(repository.closePendingRequests(9L, SessionChangeAction.CANCEL)).thenReturn(java.util.List.of());
        Object result = service.change(staff, 9L, Map.of("action", "CANCEL", "reason", "Nghỉ học"), null);
        assertTrue(result instanceof Map<?, ?>);
        Map<?, ?> response = (Map<?, ?>) result;
        assertEquals(11L, response.get("id"));
        assertTrue(response.containsKey("replacementId"));
        assertNull(response.get("replacementId"));
        verify(repository).cancelSession(9L);
        verify(repository).closePendingRequests(9L, SessionChangeAction.CANCEL);
        verify(audit).record(staff, "CANCEL", "session_exceptions", 11L);
    }
}
