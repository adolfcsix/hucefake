package vn.huce.service;

import java.util.Map;
import java.time.LocalDateTime;
import vn.huce.model.SessionAdjustmentSnapshot;
import vn.huce.model.SessionLecturerScope;
import vn.huce.model.SessionSectionSnapshot;
import vn.huce.model.SchedulingRoom;
import vn.huce.model.SemesterDateRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.SessionRequestStatus;
import vn.huce.model.SessionChangeAction;
import vn.huce.model.SessionRequestSnapshot;
import vn.huce.model.SessionRequestResolution;
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
        when(repository.findRequest(7L)).thenReturn(new SessionRequestSnapshot(
                7L, 9L, 8L, 3L, null, SessionRequestStatus.PENDING, SessionChangeAction.CANCEL));
        service.decide(staff, 7L, Map.of("action", "RETURNED", "reason", "Giữ lịch hiện tại"));
        verify(repository).decideRequest(7L, SessionRequestStatus.RETURNED, "Giữ lịch hiện tại");
        verify(notifications).notifyStaffProfile(8L, "Đề nghị đổi lịch đã được trả lại. Giữ lịch hiện tại");
        verify(audit).record(staff, "RETURNED", "session_change_requests", 7L);
        verify(repository, never()).cancelSession(anyLong());
    }

    @Test
    void mismatchedDecisionDoesNotAdjustSession() {
        when(repository.findRequest(7L)).thenReturn(new SessionRequestSnapshot(
                7L, 9L, 8L, 3L, null, SessionRequestStatus.PENDING, SessionChangeAction.CANCEL));
        assertEquals(400, assertThrows(ApiFailure.class, () -> service.decide(staff, 7L,
                Map.of("action", "RESCHEDULE", "reason", "Đổi lịch"))).status());
        verify(repository, never()).findSession(anyLong());
        verify(repository, never()).decideRequest(anyLong(), any(), anyString());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void requestCampusScopeIsCheckedBeforeDecision() {
        when(repository.findRequest(7L)).thenReturn(new SessionRequestSnapshot(
                7L, 9L, 8L, 3L, 5L, SessionRequestStatus.PENDING, SessionChangeAction.CANCEL));
        doThrow(new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này."))
                .when(scheduling).requireCampus(staff, 5L);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.decide(staff, 7L,
                Map.of("action", "RETURNED", "reason", "Kiểm tra lại"))).status());
        verify(repository, never()).decideRequest(anyLong(), any(), anyString());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void processedRequestCannotBeDecidedAgain() {
        when(repository.findRequest(7L)).thenReturn(new SessionRequestSnapshot(
                7L, 9L, 8L, 3L, null, SessionRequestStatus.APPROVED, SessionChangeAction.CANCEL));
        assertEquals(409, assertThrows(ApiFailure.class, () -> service.decide(staff, 7L,
                Map.of("action", "RETURNED", "reason", "Kiểm tra lại"))).status());
        verify(repository, never()).decideRequest(anyLong(), any(), anyString());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void unrelatedLecturerCannotRequestChange() {
        Actor lecturer = new Actor(5L, "lecturer", "LECTURER", 8L, 3L);
        when(repository.findSessionWithStaff(9L)).thenReturn(new SessionLecturerScope(
                10L, 4L, "PLANNED", Set.of(12L, 13L)));
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.request(lecturer, 9L,
                Map.of("action", "CANCEL", "reason", "Nghỉ học"))).status());
        verifyNoInteractions(sections, audit, notifications);
        verify(repository, never()).insertRequest(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void assignedAssistantMayRequestChangeAndKeepsIdResponse() {
        Actor lecturer = new Actor(5L, "lecturer", "LECTURER", 8L, 3L);
        when(repository.findSessionWithStaff(9L)).thenReturn(new SessionLecturerScope(
                10L, 4L, "PLANNED", Set.of(12L, 8L)));
        when(sections.findSectionForSession(10L)).thenReturn(new SessionSectionSnapshot(
                10L, 7L, "HP01", "OPEN", 30, "LECTURE", 3L));
        when(repository.insertRequest(9L, 8L, SessionChangeAction.CANCEL, "Nghỉ học")).thenReturn(17L);
        assertEquals(Map.of("id", 17L), service.request(lecturer, 9L,
                Map.of("action", "CANCEL", "reason", "Nghỉ học")));
        verify(audit).record(lecturer, "REQUEST_CHANGE", "class_sessions", 9L);
        verify(notifications).notifyScopedStaff(3L, 4L,
                "Có đề nghị đổi lịch cho lớp HP01. Mở Đề nghị đổi lịch để xử lý.");
    }

    @Test
    void lecturerCannotRequestRoomChange() {
        Actor lecturer = new Actor(5L, "lecturer", "LECTURER", 8L, 3L);
        when(repository.findSessionWithStaff(9L)).thenReturn(new SessionLecturerScope(
                10L, null, "PLANNED", Set.of(8L)));
        when(sections.findSectionForSession(10L)).thenReturn(new SessionSectionSnapshot(
                10L, 7L, "HP01", "OPEN", 30, "LECTURE", 3L));
        assertEquals(400, assertThrows(ApiFailure.class, () -> service.request(lecturer, 9L,
                Map.of("action", "ROOM_CHANGE", "reason", "Đổi phòng"))).status());
        verify(repository, never()).insertRequest(anyLong(), anyLong(), any(), anyString());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void adjustmentChecksOriginalCampusBeforeAnyWrite() {
        when(repository.findSession(9L)).thenReturn(new SessionAdjustmentSnapshot(
                10L, 8L, 4L, 5L, "PLANNED", LocalDateTime.of(2026, 11, 30, 7, 0),
                1, 2, 6L));
        when(sections.findSectionForSession(10L)).thenReturn(new SessionSectionSnapshot(
                10L, 7L, "HP01", "OPEN", 30, "LECTURE", 3L));
        doThrow(new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này."))
                .when(scheduling).requireCampus(staff, 5L);
        assertEquals(403, assertThrows(ApiFailure.class, () -> service.change(staff, 9L,
                Map.of("action", "CANCEL", "reason", "Nghỉ học"), null)).status());
        verify(repository, never()).cancelSession(anyLong());
        verifyNoInteractions(audit, notifications);
    }

    @Test
    void replacementUsesTypedSectionCapacityAndPreservesSourceVersion() {
        LocalDate date = LocalDate.of(2026, 11, 30);
        var original = new SessionAdjustmentSnapshot(10L, 8L, 4L, 5L, "PLANNED",
                date.atTime(7, 0), 1, 2, 6L);
        var room = new SchedulingRoom(11L, 5L, "P11", 40, "LECTURE", "ACTIVE");
        var context = new SchedulingService.Context(Map.of(), Map.of(11L, room),
                List.of(), List.of(), List.of(), List.of());
        var slot = new SchedulingService.Slot(0L, 10L, 8L, 11L, date.getDayOfWeek().getValue(), 1, 2, date, date);
        var span = new SchedulingService.Span(date.atTime(7, 0), date.atTime(8, 40));
        when(repository.findSession(9L)).thenReturn(original);
        when(sections.findSectionForSession(10L)).thenReturn(new SessionSectionSnapshot(
                10L, 7L, "HP01", "OPEN", 30, "LECTURE", 3L));
        when(repository.semesterDates(7L)).thenReturn(new SemesterDateRange(date, date.plusDays(1)));
        when(scheduling.context()).thenReturn(context);
        when(scheduling.fits(room, 30, "LECTURE")).thenReturn(true);
        when(scheduling.interval(context, slot, date)).thenReturn(span);
        when(scheduling.actualConflicts(context, slot, span, 9L)).thenReturn(List.of());
        when(repository.insertReplacement(10L, 8L, 11L, 1, 2, span.start(), span.end(), 6L, 9L))
                .thenReturn(12L);
        when(repository.insertException(9L, date, SessionChangeAction.ROOM_CHANGE, 12L, "Đổi phòng", 1L))
                .thenReturn(13L);
        assertEquals(Map.of("id", 13L, "replacementId", 12L), service.change(staff, 9L,
                Map.of("action", "ROOM_CHANGE", "roomId", 11L, "reason", "Đổi phòng"), null));
        verify(scheduling).fits(room, 30, "LECTURE");
        verify(repository).cancelSession(9L);
        verify(audit).record(staff, "ROOM_CHANGE", "session_exceptions", 13L);
    }

    @Test
    void directCancellationKeepsNullReplacementAndClosesPendingRequests() {
        when(repository.findSession(9L)).thenReturn(new SessionAdjustmentSnapshot(
                10L, 8L, null, null, "PLANNED", LocalDateTime.of(2026, 11, 30, 7, 0),
                1, 2, null));
        when(sections.findSectionForSession(10L)).thenReturn(new SessionSectionSnapshot(
                10L, 7L, "HP01", "OPEN", 30, "LECTURE", 3L));
        when(repository.insertException(eq(9L), any(), eq(SessionChangeAction.CANCEL),
                isNull(), eq("Nghỉ học"), eq(1L))).thenReturn(11L);
        when(repository.closePendingRequests(9L, SessionChangeAction.CANCEL)).thenReturn(java.util.List.of(
                new SessionRequestResolution(13L, 8L, SessionRequestStatus.APPROVED, "Đã xử lý"),
                new SessionRequestResolution(14L, 9L, SessionRequestStatus.RETURNED, "Đã điều chỉnh khác")));
        Object result = service.change(staff, 9L, Map.of("action", "CANCEL", "reason", "Nghỉ học"), null);
        assertTrue(result instanceof Map<?, ?>);
        Map<?, ?> response = (Map<?, ?>) result;
        assertEquals(11L, response.get("id"));
        assertTrue(response.containsKey("replacementId"));
        assertNull(response.get("replacementId"));
        verify(repository).cancelSession(9L);
        verify(repository).closePendingRequests(9L, SessionChangeAction.CANCEL);
        verify(audit).record(staff, "CANCEL", "session_exceptions", 11L);
        verify(audit).record(staff, "APPROVED", "session_change_requests", 13L);
        verify(audit).record(staff, "RETURNED", "session_change_requests", 14L);
        verify(notifications).notifyStaffProfile(8L, "Đã xử lý");
        verify(notifications).notifyStaffProfile(9L, "Đã điều chỉnh khác");
    }
}
