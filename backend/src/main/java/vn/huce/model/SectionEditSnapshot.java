package vn.huce.model;

import java.time.LocalDate;
import java.util.Map;

/** Typed fields for section writes; retain the original projection for version-conflict responses. */
public record SectionEditSnapshot(long id, long semesterId, Long managingUnitId, String status,
                                  long version, long scheduled, int sessionsPerWeek, int periodsPerSession,
                                  Long staffId, LocalDate startDate, LocalDate endDate,
                                  Map<String, Object> conflictDetails) {
}
