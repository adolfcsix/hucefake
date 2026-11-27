package vn.huce.model;

/** Persisted exchange identifier and UTC timestamp in the existing API format. */
public record ChatExchangeReceipt(long id, String createdAt) {
}
