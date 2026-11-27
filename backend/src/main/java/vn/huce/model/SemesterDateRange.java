package vn.huce.model;

import java.time.LocalDate;

/** Inclusive academic semester date range. */
public record SemesterDateRange(LocalDate startDate, LocalDate endDate) {
    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
