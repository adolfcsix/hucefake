package vn.huce.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static vn.huce.service.SchedulingService.*;
import static vn.huce.validation.Inputs.n;
import static vn.huce.validation.Inputs.s;

/** Calendar calculations shared by manual scheduling and the genetic solver. */
final class ScheduleCalendar {
    private ScheduleCalendar() {
    }

    static LocalDate firstDay(LocalDate from, int dayOfWeek) {
        return from.plusDays(Math.floorMod(dayOfWeek - from.getDayOfWeek().getValue(), 7));
    }

    static List<LocalDate> occurrences(Slot slot) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = firstDay(slot.from(), slot.day()); !date.isAfter(slot.to()); date = date.plusWeeks(1)) {
            dates.add(date);
        }
        return dates;
    }

    static boolean holiday(Context context, long campusId, LocalDate date) {
        return context.holidays().stream().anyMatch(row ->
                (row.get("campus_id") == null || n(row, "campus_id") == campusId)
                        && !day(row.get("start_date")).isAfter(date)
                        && !day(row.get("end_date")).isBefore(date));
    }

    static Span interval(Context context, Slot slot, LocalDate date) {
        Map<String, Object> room = context.rooms().get(slot.room());
        if (room == null || slot.first() < 1 || slot.count() < 1 || slot.count() > 6 || slot.last() > 15) {
            return null;
        }

        LocalTime start = null;
        LocalTime end = null;
        String shift = null;
        for (int period = slot.first(); period <= slot.last(); period++) {
            Map<String, Object> selected = null;
            for (Map<String, Object> definition : context.periods()) {
                if (n(definition, "campus_id") == n(room, "campus_id")
                        && n(definition, "period_no") == period
                        && !day(definition.get("effective_from")).isAfter(date)
                        && (definition.get("effective_to") == null
                            || !day(definition.get("effective_to")).isBefore(date))) {
                    selected = definition;
                    break;
                }
            }
            if (selected == null) {
                return null;
            }
            LocalTime periodStart = LocalTime.parse(s(selected, "start_time"));
            LocalTime periodEnd = LocalTime.parse(s(selected, "end_time"));
            if (end != null && (!Objects.equals(shift, selected.get("shift")) || end.isAfter(periodStart))) {
                return null;
            }
            if (start == null) {
                start = periodStart;
            }
            end = periodEnd;
            shift = s(selected, "shift");
        }
        return new Span(date.atTime(start), date.atTime(end));
    }
}
