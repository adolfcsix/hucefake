package vn.huce.model;

/** Snapshot of unread notifications; lastId is null when no notification is unread. */
public record UnreadNotificationSummary(long count, Long lastId) {
}
