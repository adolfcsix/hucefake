package vn.huce.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record PeriodDraft(long campusId, long periodNumber, LocalDate effectiveFrom,
                          LocalTime startTime, LocalTime endTime, String shift) {
}
