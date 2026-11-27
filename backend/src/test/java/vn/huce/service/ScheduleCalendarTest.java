package vn.huce.service;

import vn.huce.model.SchedulingRoom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.model.TeachingPeriod;
import vn.huce.model.NonTeachingDay;

import static org.junit.jupiter.api.Assertions.*;
import static vn.huce.service.SchedulingService.*;

class ScheduleCalendarTest {
    private final LocalDate monday = LocalDate.of(2026, 11, 30);

    private Slot slot(int first, int count) {
        return new Slot(1L, 2L, 3L, 4L, 1, first, count, monday, monday.plusWeeks(2));
    }

    private TeachingPeriod period(int number, String start, String end, String shift, String effectiveFrom) {
        return new TeachingPeriod(5L, number, LocalTime.parse(start), LocalTime.parse(end),
                shift, LocalDate.parse(effectiveFrom), null);
    }

    private Context context(List<TeachingPeriod> periods, List<NonTeachingDay> holidays) {
        return new Context(Map.of(), Map.of(4L, new SchedulingRoom(4L, 5L, "P4", 40, "LECTURE", "ACTIVE")),
                periods, List.of(), holidays, List.of());
    }

    @Test
    void occurrencesIncludeBothBoundaries() {
        assertEquals(List.of(monday, monday.plusWeeks(1), monday.plusWeeks(2)),
                ScheduleCalendar.occurrences(slot(1, 2)));
        assertEquals(monday.plusWeeks(1), ScheduleCalendar.firstDay(monday.plusDays(1), 1));
    }

    @Test
    void consecutivePeriodsWithinSameShiftFormOneInterval() {
        var context = context(List.of(period(1, "07:00", "07:50", "MORNING", "2026-09-01"),
                period(2, "08:00", "08:50", "MORNING", "2026-09-01")), List.of());
        assertEquals(new Span(LocalDateTime.of(2026, 11, 30, 7, 0),
                LocalDateTime.of(2026, 11, 30, 8, 50)), ScheduleCalendar.interval(context, slot(1, 2), monday));
    }

    @Test
    void crossingShiftsIsRejected() {
        var context = context(List.of(period(1, "11:00", "11:50", "MORNING", "2026-09-01"),
                period(2, "13:00", "13:50", "AFTERNOON", "2026-09-01")), List.of());
        assertNull(ScheduleCalendar.interval(context, slot(1, 2), monday));
    }

    @Test
    void overlappingPeriodsAndMissingPeriodsAreRejected() {
        var context = context(List.of(period(1, "07:00", "08:00", "MORNING", "2026-09-01"),
                period(2, "07:50", "08:50", "MORNING", "2026-09-01")), List.of());
        assertNull(ScheduleCalendar.interval(context, slot(1, 2), monday));
        assertNull(ScheduleCalendar.interval(context, slot(2, 2), monday));
    }

    @Test
    void futurePeriodDefinitionDoesNotAffectEarlierDates() {
        var context = context(List.of(period(1, "09:00", "09:50", "MORNING", "2026-12-01"),
                period(1, "07:00", "07:50", "MORNING", "2026-09-01")), List.of());
        assertEquals(monday.atTime(7, 0), ScheduleCalendar.interval(context, slot(1, 1), monday).start());
        assertEquals(monday.plusWeeks(1).atTime(9, 0),
                ScheduleCalendar.interval(context, slot(1, 1), monday.plusWeeks(1)).start());
    }

    @Test
    void expiredPeriodDefinitionIsNotSelectedAfterInclusiveEndDate() {
        var expired = new TeachingPeriod(5L, 1, LocalTime.of(9, 0), LocalTime.of(9, 50),
                "MORNING", monday.minusWeeks(1), monday);
        var original = period(1, "07:00", "07:50", "MORNING", "2026-09-01");
        var context = context(List.of(expired, original), List.of());
        assertEquals(monday.atTime(9, 0), ScheduleCalendar.interval(context, slot(1, 1), monday).start());
        assertEquals(monday.plusWeeks(1).atTime(7, 0),
                ScheduleCalendar.interval(context, slot(1, 1), monday.plusWeeks(1)).start());
    }

    @Test
    void globalHolidayAppliesToAllCampuses() {
        var context = context(List.of(), List.of(new NonTeachingDay(null, monday, monday)));
        assertTrue(ScheduleCalendar.holiday(context, 5L, monday));
        assertTrue(ScheduleCalendar.holiday(context, 6L, monday));
        assertFalse(ScheduleCalendar.holiday(context, 5L, monday.plusDays(1)));
    }

    @Test
    void campusHolidayAppliesOnlyToThatCampusAndIncludesEndDate() {
        var context = context(List.of(), List.of(new NonTeachingDay(5L, monday, monday.plusDays(1))));
        assertTrue(ScheduleCalendar.holiday(context, 5L, monday.plusDays(1)));
        assertFalse(ScheduleCalendar.holiday(context, 6L, monday));
        assertFalse(ScheduleCalendar.holiday(context, 5L, monday.plusDays(2)));
    }
}
