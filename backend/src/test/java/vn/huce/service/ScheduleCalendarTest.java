package vn.huce.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static vn.huce.service.SchedulingService.*;

class ScheduleCalendarTest {
    private final LocalDate monday = LocalDate.of(2026, 11, 30);

    private Slot slot(int first, int count) {
        return new Slot(1L, 2L, 3L, 4L, 1, first, count, monday, monday.plusWeeks(2));
    }

    private Map<String, Object> period(int number, String start, String end, String shift, String effectiveFrom) {
        return Map.of("campus_id", 5L, "period_no", number, "start_time", start,
                "end_time", end, "shift", shift, "effective_from", effectiveFrom);
    }

    private Context context(List<Map<String, Object>> periods, List<Map<String, Object>> holidays) {
        return new Context(Map.of(), Map.of(4L, Map.of("campus_id", 5L)),
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
    void campusHolidayAppliesOnlyToThatCampusAndIncludesEndDate() {
        var context = context(List.of(), List.of(Map.of("campus_id", 5L,
                "start_date", "2026-11-30", "end_date", "2026-12-01")));
        assertTrue(ScheduleCalendar.holiday(context, 5L, monday.plusDays(1)));
        assertFalse(ScheduleCalendar.holiday(context, 6L, monday));
        assertFalse(ScheduleCalendar.holiday(context, 5L, monday.plusDays(2)));
    }
}
