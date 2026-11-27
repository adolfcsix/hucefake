package vn.huce.model;

/** Internal persisted approval data used by the mutation workflow. */
public record ApprovalRequestSnapshot(long id, long requestedBy, long workItemId,
                                      ApprovalStatus status, String payloadJson) {
}
