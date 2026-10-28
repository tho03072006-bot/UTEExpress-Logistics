package vn.edu.hcmute.uteexpress.controller.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.hcmute.uteexpress.dto.assistant.ChatConversationResponse.ErrorResponse;
import vn.edu.hcmute.uteexpress.service.assistant.AssistantFailure;

@RestControllerAdvice(assignableTypes=AssistantChatController.class)
@Order(-100)
public class AssistantExceptionHandler {
    @ExceptionHandler(AssistantFailure.class)
    public ResponseEntity<ErrorResponse> failed(AssistantFailure failure) {
        return ResponseEntity.status(failure.getStatus()).cacheControl(CacheControl.noStore()).header("Retry-After",Long.toString(failure.getRetryAfter()))
                .body(new ErrorResponse(failure.getCode(),failure.getMessage(),failure.getRetryAfter()));
    }
    @ExceptionHandler({ResponseStatusException.class,org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> unauthorized(Exception error) {
        int status=error instanceof ResponseStatusException response ? response.getStatusCode().value() : 403;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ErrorResponse("SESSION_EXPIRED","Phiên hoặc quyền truy cập đã thay đổi. Vui lòng đăng nhập lại.",0));
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception error) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new ErrorResponse("INVALID_INPUT","Dữ liệu không hợp lệ. Kiểm tra câu hỏi (tối đa 1200 ký tự), mã yêu cầu hoặc tham số chọn đơn.",0));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unavailable(Exception error) {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new ErrorResponse("UNAVAILABLE","Trợ lý AI tạm thời chưa phản hồi. Vui lòng thử lại.",5));
    }
}
