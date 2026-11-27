package vn.huce.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted recurring pattern, including the campus only when requested. */
public record SchedulePatternSnapshot(
        long id, long sectionId, long staffId, long roomId,
        int dayOfWeek, int periodFrom, int periodCount,
        LocalDate effectiveFrom, LocalDate effectiveTo, String source,
        Long runId, long createdBy, LocalDateTime createdAt, int version, Long campusId
) {
    /** Preserve the existing database-column JSON contract for VERSION_CONFLICT. */
    public Map<String, Object> conflictDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", id);
        details.put("section_id", sectionId);
        details.put("staff_id", staffId);
        details.put("room_id", roomId);
        details.put("day_of_week", dayOfWeek);
        details.put("period_from", periodFrom);
        details.put("period_count", periodCount);
        details.put("effective_from", effectiveFrom.toString());
        details.put("effective_to", effectiveTo.toString());
        details.put("source", source);
        details.put("run_id", runId);
        details.put("created_by", createdBy);
        details.put("created_at", createdAt.toString());
        details.put("version", version);
        if (campusId != null) {
            details.put("campus_id", campusId);
        }
        return details;
    }
}
