package vn.huce;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiFailure extends RuntimeException {
    final int status;
    final String code;
    final Object details;
    ApiFailure(int status,String code,String message) { this(status,code,message,Map.of()); }
    ApiFailure(int status,String code,String message,Object details) { super(message);this.status=status;this.code=code;this.details=details; }
}
@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<?> failure(ApiFailure e) {
        return ResponseEntity.status(e.status).body(Map.of("code",e.code,"message",e.getMessage(),"details",e.details,"request_id",UUID.randomUUID().toString()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> integrity(DataIntegrityViolationException e) {
        var cause=e.getMostSpecificCause();
        if(cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState()))
            return failure(new ApiFailure(409,"DUPLICATE_REQUEST","Mã hoặc bản ghi đã tồn tại."));
        return failure(new ApiFailure(400,"VALIDATION_ERROR","Dữ liệu không hợp lệ hoặc không đáp ứng ràng buộc."));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception e){return failure(new ApiFailure(400,"VALIDATION_ERROR","Dữ liệu không hợp lệ."));}
}
