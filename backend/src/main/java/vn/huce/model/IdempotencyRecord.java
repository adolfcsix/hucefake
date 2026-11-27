package vn.huce.model;

/** Stored response for a completed write, using its original canonical request fingerprint. */
public record IdempotencyRecord(String fingerprint, String responseJson) {
}
