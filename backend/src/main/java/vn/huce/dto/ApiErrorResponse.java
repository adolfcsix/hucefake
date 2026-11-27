package vn.huce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiErrorResponse(String code, String message, Object details,
                               @JsonProperty("request_id") String requestId) {
}
