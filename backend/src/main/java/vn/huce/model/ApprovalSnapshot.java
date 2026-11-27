package vn.huce.model;

/**
 * A recurring pattern captured at submission. JSON field names are retained
 * for compatibility with approval payloads already stored in PostgreSQL.
 */
public record ApprovalSnapshot(long id, long section, Long staff, long room,
                               int day, int first, int count, String from, String to,
                               Object version, Object sectionCode, Object subjectName, Object roomCode) {
}
