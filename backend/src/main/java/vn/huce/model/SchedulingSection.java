package vn.huce.model;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** Internal scheduling projection, separate from public section response rows. */
public record SchedulingSection(long id, long semesterId, Long managingUnitId, String status,
                                String code, String subjectName, Long staffId, int capacity,
                                String requiredRoomType, int sessionsPerWeek, int periodsPerSession,
                                long scheduled, LocalDate startDate, LocalDate endDate,
                                Set<Long> assistantIds, Map<Long, String> groups) {
}
