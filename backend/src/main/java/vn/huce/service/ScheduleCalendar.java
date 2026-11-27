package vn.huce.service;

import vn.huce.model.SchedulingRoom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import vn.huce.model.TeachingPeriod;

import static vn.huce.service.SchedulingService.*;

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
                (row.campusId() == null || row.campusId() == campusId)
                        && !row.startDate().isAfter(date)
                        && !row.endDate().isBefore(date));
    }

    static Span interval(Context context, Slot slot, LocalDate date) {
        SchedulingRoom room = context.rooms().get(slot.room());
        if (room == null || slot.first() < 1 || slot.count() < 1 || slot.count() > 6 || slot.last() > 15) {
            return null;
        }

        LocalTime start = null;
        LocalTime end = null;
        String shift = null;
        for (int period = slot.first(); period <= slot.last(); period++) {
            TeachingPeriod selected = null;
            for (TeachingPeriod definition : context.periods()) {
                if (definition.campusId() == room.campusId()
                        && definition.periodNo() == period && definition.effectiveOn(date)) {
                    selected = definition;
                    break;
                }
            }
            if (selected == null) {
                return null;
            }
            LocalTime periodStart = selected.startTime();
            LocalTime periodEnd = selected.endTime();
            if (end != null && (!Objects.equals(shift, selected.shift()) || end.isAfter(periodStart))) {
                return null;
            }
            if (start == null) {
                start = periodStart;
            }
            end = periodEnd;
            shift = selected.shift();
        }
        return new Span(date.atTime(start), date.atTime(end));
    }
}
