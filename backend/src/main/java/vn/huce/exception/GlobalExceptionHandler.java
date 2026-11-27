package vn.huce.exception;

import java.sql.SQLException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.huce.dto.ApiErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiFailure.class)
    public ResponseEntity<ApiErrorResponse> failure(ApiFailure error) {
        ApiErrorResponse response = new ApiErrorResponse(error.code(), error.getMessage(),
                error.details(), UUID.randomUUID().toString());
        return ResponseEntity.status(error.status()).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> integrity(DataIntegrityViolationException error) {
        Throwable cause = error.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            return failure(new ApiFailure(409, "DUPLICATE_REQUEST", "Mã hoặc bản ghi đã tồn tại."));
        }
        return failure(new ApiFailure(400, "VALIDATION_ERROR",
                "Dữ liệu không hợp lệ hoặc không đáp ứng ràng buộc."));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> malformed(Exception error) {
        return failure(new ApiFailure(400, "VALIDATION_ERROR", "Dữ liệu không hợp lệ."));
    }
}
