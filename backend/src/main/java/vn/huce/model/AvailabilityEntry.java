package vn.huce.model;

public record AvailabilityEntry(long dayOfWeek, long periodFrom, long periodTo,
                                Kind kind, String reason) {
    public enum Kind {
        BUSY, AVOID
    }
}
