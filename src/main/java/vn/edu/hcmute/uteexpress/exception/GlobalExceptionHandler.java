package vn.edu.hcmute.uteexpress.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Bat loi tap trung cho toan he thong, tranh moi Controller phai try/catch rieng.
 * TODO: tach rieng xu ly cho loi validation (@Valid) va tra ve view loi dep hon
 * cho cac request HTML (khac voi request API tra ve JSON nhu hien tai).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<String> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Loi he thong: " + ex.getMessage());
    }
}
