package vn.huce.model;

import java.time.LocalDate;
import java.time.LocalTime;

/** Active period definition used by manual and automatic scheduling. */
public record TeachingPeriod(long campusId, int periodNo, LocalTime startTime, LocalTime endTime,
                             String shift, LocalDate effectiveFrom, LocalDate effectiveTo) {
    public boolean effectiveOn(LocalDate date) {
        return !effectiveFrom.isAfter(date) && (effectiveTo == null || !effectiveTo.isBefore(date));
    }
}
