package vn.huce.model;

/** Internal timetable version fields required by the approval workflow. */
public record TimetableVersionSnapshot(long id, long semesterId, int versionNo,
                                       TimetableVersionStatus status, int sectionCount, long createdBy) {
}
