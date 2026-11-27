package vn.huce.model;

import java.time.LocalDate;

/** Validated recurring schedule coordinates, independent of the HTTP and solver layers. */
public record PatternDraft(long sectionId, Long staffId, long roomId, int dayOfWeek,
                           int periodFrom, int periodCount, LocalDate effectiveFrom,
                           LocalDate effectiveTo) {
}
