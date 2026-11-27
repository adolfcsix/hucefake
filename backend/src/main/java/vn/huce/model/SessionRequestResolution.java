package vn.huce.model;

/** Result of resolving a pending request during a direct session adjustment. */
public record SessionRequestResolution(long id, long staffId, SessionRequestStatus status, String reason) {
}
