package vn.huce.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginRequest(String username, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=<redacted>]";
    }
}
