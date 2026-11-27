package vn.huce.exception;

import java.util.Map;

public class ApiFailure extends RuntimeException {
    private final int status;
    private final String code;
    private final Object details;

    public ApiFailure(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiFailure(int status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Object details() {
        return details;
    }
}
