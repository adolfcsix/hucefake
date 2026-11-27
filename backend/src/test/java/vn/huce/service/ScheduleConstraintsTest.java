package vn.huce.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.model.NonTeachingDay;
import vn.huce.model.StaffAvailability;
import vn.huce.model.AvailabilityEntry.Kind;

import static org.junit.jupiter.api.Assertions.*;
import static vn.huce.service.SchedulingService.*;

class ScheduleConstraintsTest {
    private final LocalDate monday = LocalDate.of(2026, 11, 30);

    private Slot slot(long id, long section, long room, int first, int count) {
        return new Slot(id, section, 3L, room, 1, first, count, monday, monday);
    }

    private Map<String, Object> section(String code) {
        return Map.of("code", code, "staff_id", 3L, "assistant_ids", new Long[]{8L},
                "group_ids", new Long[]{10L}, "group_codes", new String[]{"K66"},
                "capacity", 30, "required_room_type", "THEORY", "semester_id", 1L,
                "start_date", monday.toString(), "end_date", monday.toString());
    }

    private Context context(List<NonTeachingDay> holidays, List<StaffAvailability> availability) {
        return new Context(Map.of(1L, section("A"), 2L, section("B")),
                Map.of(4L, Map.of("code", "P1", "campus_id", 5L, "status", "ACTIVE",
                        "capacity", 40, "type", "THEORY")),
                List.of(), availability, holidays, List.of());
    }

    @Test
    void sameRoomStaffAndGroupConflictsKeepRuleOrderAndCodes() {
        var issues = ScheduleConstraints.pair(context(List.of(), List.of()),
                slot(1, 1, 4, 1, 2), slot(2, 2, 4, 2, 2));
        assertEquals(List.of("BR04", "BR05", "BR06"), issues.stream().map(row -> row.get("rule")).toList());
        assertEquals(List.of("K66"), issues.get(2).get("groupCodes"));
        assertEquals(2L, issues.get(0).get("patternId"));
    }

    @Test
    void sameSectionOnSameDayConflictsEvenWhenPeriodsDoNotOverlap() {
        var issues = ScheduleConstraints.pair(context(List.of(), List.of()),
                slot(1, 1, 4, 1, 1), slot(2, 1, 4, 3, 1));
        assertEquals(List.of("BR09"), issues.stream().map(row -> row.get("rule")).toList());
    }

    @Test
    void campusHolidayRemovesPairConflicts() {
        var holidays = List.of(new NonTeachingDay(5L, monday, monday));
        assertTrue(ScheduleConstraints.pair(context(holidays, List.of()),
                slot(1, 1, 4, 1, 2), slot(2, 2, 4, 1, 2)).isEmpty());
    }

    @Test
    void availabilityMatchesSemesterDayStaffAndInclusivePeriodBoundaries() {
        var rows = List.of(new StaffAvailability(8L, 2L, 1, 1, 2, Kind.BUSY),
                new StaffAvailability(8L, 1L, 2, 1, 2, Kind.BUSY),
                new StaffAvailability(99L, 1L, 1, 1, 2, Kind.BUSY),
                new StaffAvailability(8L, 1L, 1, 3, 4, Kind.BUSY));
        var context = context(List.of(), rows);
        assertFalse(ScheduleConstraints.busy(context, slot(1, 1, 4, 1, 2), Kind.BUSY));
        assertTrue(ScheduleConstraints.busy(context, slot(1, 1, 4, 2, 2), Kind.BUSY));
    }

    @Test
    void assistantAvailabilityIsIncludedAndAvoidIsNotBusy() {
        var rows = List.of(new StaffAvailability(8L, 1L, 1, 2, 3, Kind.AVOID));
        var context = context(List.of(), rows);
        assertTrue(ScheduleConstraints.busy(context, slot(1, 1, 4, 1, 2), Kind.AVOID));
        assertFalse(ScheduleConstraints.busy(context, slot(1, 1, 4, 1, 2), Kind.BUSY));
    }
}
