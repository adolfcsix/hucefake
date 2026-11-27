package vn.huce.model;

import java.time.LocalDateTime;

/** Persisted original session coordinates and nullable links for a schedule adjustment. */
public record SessionAdjustmentSnapshot(long sectionId, long staffId, Long roomId, Long campusId,
                                        String status, LocalDateTime startAt, int periodFrom,
                                        int periodCount, Long timetableVersionId) {
}
