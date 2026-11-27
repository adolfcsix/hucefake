package vn.huce.model;

/** Persisted decision context for a session change request. */
public record SessionRequestSnapshot(long id, long sessionId, long staffId, Long managingUnitId,
                                     Long campusId, SessionRequestStatus status, SessionChangeAction action) {
}
