package vn.huce.model;

/** Recurring pattern snapshot; existing JSON field names and date strings are preserved. */
public record ApprovalSnapshot(long id, long section, Long staff, long room,
                               int day, int first, int count, String from, String to,
                               int version, String sectionCode, String subjectName, String roomCode) {
}
