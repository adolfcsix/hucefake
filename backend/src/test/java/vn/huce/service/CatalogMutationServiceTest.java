package vn.huce.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.RoomDraft;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.CatalogMutationRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogMutationServiceTest {
    private final CatalogMutationRepository repository = mock(CatalogMutationRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final SchedulingService scheduling = mock(SchedulingService.class);
    private final CatalogMutationService service = new CatalogMutationService(repository, audit, scheduling);
    private final Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);

    private Map<String, Object> roomInput() {
        return Map.of("campusId", 4L, "code", " a101 ", "name", "Phòng A101",
                "capacity", 40, "type", "LECTURE", "status", "ACTIVE");
    }

    @Test
    void createsNormalizedRoomAndRecordsAudit() {
        RoomDraft draft = new RoomDraft(4L, "A101", "Phòng A101", 40, "LECTURE", "ACTIVE");
        when(repository.insertRoom(draft)).thenReturn(Map.of("id", 7L));
        assertEquals(Map.of("id", 7L), service.room(staff, roomInput(), null));
        verify(scheduling).requireCampus(staff, 4L);
        verify(audit).record(staff, "SAVE", "teaching_rooms", 7L);
    }

    @Test
    void rejectsRoomCampusTransfer() {
        when(repository.findRoom(7L)).thenReturn(Map.of("campus_id", 8L));
        assertThrows(ApiFailure.class, () -> service.room(staff, roomInput(), 7L));
        verify(repository, never()).updateRoom(anyLong(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsRoomUpdateWithConflictingSessions() {
        when(repository.findRoom(7L)).thenReturn(Map.of("campus_id", 4L));
        when(repository.affectedRoomSessions(eq(7L), any())).thenReturn(List.of(Map.of("id", 9L)));
        ApiFailure failure = assertThrows(ApiFailure.class, () -> service.room(staff, roomInput(), 7L));
        assertEquals("SCHEDULE_CONFLICT", failure.code());
        verify(repository, never()).updateRoom(anyLong(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsReversedHolidayRange() {
        assertThrows(ApiFailure.class, () -> service.holiday(staff, Map.of("campusId", 4L,
                "startDate", "2026-12-03", "endDate", "2026-12-01", "name", "Nghỉ"), null));
        verifyNoInteractions(repository, audit);
    }

    @Test
    void rejectsPeriodEndingBeforeStart() {
        assertThrows(ApiFailure.class, () -> service.createPeriod(staff, Map.of("campusId", 4L,
                "periodNo", 1, "effectiveFrom", LocalDate.now().plusDays(1).toString(),
                "startTime", "09:00", "endTime", "08:00", "shift", "MORNING")));
        verifyNoInteractions(repository, audit);
    }
}
