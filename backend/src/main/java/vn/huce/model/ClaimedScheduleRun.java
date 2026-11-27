package vn.huce.model;

/** Internal worker input, not an HTTP response. */
public record ClaimedScheduleRun(long id, long semesterId, long requestedBy, String parametersJson) {
}
