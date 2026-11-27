package vn.huce.service;

import vn.huce.model.SchedulingSection;
import vn.huce.model.SchedulingRoom;
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

    private SchedulingSection section(long id, String code) {
        return new SchedulingSection(id, 1L, 1L, "DRAFT", code, "Toán", 3L,
                30, "THEORY", 1, 2, 0L, monday, monday, java.util.Set.of(8L), Map.of(10L, "K66"));
    }

    private Context context(List<NonTeachingDay> holidays, List<StaffAvailability> availability) {
        return new Context(Map.of(1L, section(1L, "A"), 2L, section(2L, "B")),
                Map.of(4L, new SchedulingRoom(4L, 5L, "P1", 40, "THEORY", "ACTIVE")),
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
    void roomMustBeActiveOfRequiredTypeAndLargeEnough() {
        var section = new SchedulingSection(1L, 1L, 1L, "DRAFT", "A", "Toán", 3L,
                30, "LECTURE", 1, 2, 0L, monday, monday, java.util.Set.of(), Map.of());
        assertTrue(ScheduleConstraints.fits(new SchedulingRoom(1L, 5L, "P1", 30, "LECTURE", "ACTIVE"), section));
        assertFalse(ScheduleConstraints.fits(new SchedulingRoom(1L, 5L, "P1", 29, "LECTURE", "ACTIVE"), section));
        assertFalse(ScheduleConstraints.fits(new SchedulingRoom(1L, 5L, "P1", 30, "LAB", "ACTIVE"), section));
        assertFalse(ScheduleConstraints.fits(new SchedulingRoom(1L, 5L, "P1", 30, "LECTURE", "MAINTENANCE"), section));
        assertFalse(ScheduleConstraints.fits(new SchedulingRoom(1L, 5L, "P1", 30, "LECTURE", "INACTIVE"), section));
        assertFalse(ScheduleConstraints.fits(null, section));
    }

    @Test
    void groupCodesRemainAssociatedWithTheirOwnIdentifiersInConflictDetails() {
        var first = new SchedulingSection(1L, 1L, 1L, "DRAFT", "A", "Toán", 3L,
                30, "THEORY", 1, 2, 0L, monday, monday, java.util.Set.of(), Map.of(20L, "K20"));
        var groups = new java.util.LinkedHashMap<Long, String>();
        groups.put(10L, "K10");
        groups.put(20L, "K20");
        var second = new SchedulingSection(2L, 1L, 1L, "DRAFT", "B", "Toán", 3L,
                30, "THEORY", 1, 2, 0L, monday, monday, java.util.Set.of(), groups);
        var context = new Context(Map.of(1L, first, 2L, second),
                Map.of(4L, new SchedulingRoom(4L, 5L, "P1", 40, "THEORY", "ACTIVE")),
                List.of(), List.of(), List.of(), List.of());
        var conflict = ScheduleConstraints.pair(context, slot(1, 1, 4, 1, 2), slot(2, 2, 4, 1, 2))
                .stream().filter(issue -> "BR06".equals(issue.get("rule"))).findFirst().orElseThrow();
        assertEquals(List.of("K20"), conflict.get("groupCodes"));
    }

    @Test
    void assistantAvailabilityIsIncludedAndAvoidIsNotBusy() {
        var rows = List.of(new StaffAvailability(8L, 1L, 1, 2, 3, Kind.AVOID));
        var context = context(List.of(), rows);
        assertTrue(ScheduleConstraints.busy(context, slot(1, 1, 4, 1, 2), Kind.AVOID));
        assertFalse(ScheduleConstraints.busy(context, slot(1, 1, 4, 1, 2), Kind.BUSY));
    }
}
