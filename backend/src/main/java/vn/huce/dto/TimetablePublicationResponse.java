package vn.huce.dto;

/** Result of a new publication; stored legacy execution results remain unchanged. */
public record TimetablePublicationResponse(long versionId, long sessions, int sections) {
}
