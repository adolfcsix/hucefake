package vn.huce.model;

import java.time.LocalDate;

/** Non-teaching date range; a null campus applies to every campus. */
public record NonTeachingDay(Long campusId, LocalDate startDate, LocalDate endDate) {
}
