package vn.huce.model;

import java.util.Set;

/** Session and assigned lecturers used to authorize requests for schedule changes. */
public record SessionLecturerScope(long sectionId, Long roomId, String status, Set<Long> staffIds) {
}
