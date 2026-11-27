package vn.huce.model;

/** Persisted conversation exchange with the current proposal state. */
public record ChatExchangeSnapshot(long id, String question, String createdAt,
                                   String replyJson, String resultJson, boolean cancelled, boolean expired) {
    public String outcome() {
        if (resultJson != null) {
            return "CONFIRMED";
        }
        if (cancelled) {
            return "CANCELLED";
        }
        return expired ? "EXPIRED" : "PENDING";
    }
}
