package vn.huce.model;

import java.time.LocalDate;

/** Latest persisted revision of a teaching period. */
public record PeriodRevision(long id, LocalDate effectiveFrom) {
}
