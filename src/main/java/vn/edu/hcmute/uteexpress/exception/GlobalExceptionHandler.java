package vn.edu.hcmute.uteexpress.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public Object denied(AccessDeniedException ex, HttpServletRequest request) {
        return response(request, 403, "Bạn không có quyền truy cập chức năng hoặc dữ liệu này.");
    }
    @ExceptionHandler(ResponseStatusException.class)
    public Object status(ResponseStatusException ex, HttpServletRequest request) {
        int code = ex.getStatusCode().value();
        return response(request, code, code == 404 ? "Không tìm thấy nội dung yêu cầu." : "Yêu cầu không hợp lệ.");
    }
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.validation.BindException.class, org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.multipart.MaxUploadSizeExceededException.class})
    public Object invalid(Exception ex, HttpServletRequest request) {
        return response(request, 400, "Thông tin gửi lên không hợp lệ hoặc tệp vượt giới hạn. Vui lòng kiểm tra và thử lại.");
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public Object conflict(Exception ex, HttpServletRequest request) {
        return response(request, 409, "Thông tin bị trùng hoặc đã thay đổi. Vui lòng tải lại và kiểm tra dữ liệu.");
    }
    @ExceptionHandler(Exception.class)
    public Object failed(Exception ex, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Không xử lý được yêu cầu: {}", ex.getClass().getSimpleName());
        return response(request, 500, "Tạm thời không xử lý được yêu cầu. Vui lòng thử lại sau.");
    }
    private Object response(HttpServletRequest request, int status, String message) {
        if (request.getRequestURI().startsWith("/api/") || String.valueOf(request.getHeader("Accept")).contains("application/json")) {
            return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
        }
        ModelAndView view = new ModelAndView("error");
        view.setStatus(HttpStatus.valueOf(status));
        view.addObject("status", status); view.addObject("message", message);
        return view;
    }
}
