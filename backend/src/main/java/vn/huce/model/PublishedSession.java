package vn.huce.model;

import java.time.LocalDateTime;
import java.util.Set;

/** Published teaching session coordinates used for conflict checks in the genetic solver. */
public record PublishedSession(long sectionId, Long roomId, LocalDateTime start,
                               LocalDateTime end, Set<Long> staffIds, Set<Long> groupIds) {
}
