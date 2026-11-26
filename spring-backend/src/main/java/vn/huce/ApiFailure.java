package vn.huce;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ApiFailure extends RuntimeException {
    final int status;
    final String code;
    ApiFailure(int status,String code,String message) { super(message); this.status=status; this.code=code; }
}
@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<?> failure(ApiFailure e) {
        return ResponseEntity.status(e.status).body(Map.of("code",e.code,"message",e.getMessage(),"details",Map.of()));
    }
}
