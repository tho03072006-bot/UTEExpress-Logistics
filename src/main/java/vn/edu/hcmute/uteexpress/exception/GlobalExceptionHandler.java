package vn.edu.hcmute.uteexpress.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Bat loi tap trung cho toan he thong, tranh moi Controller phai try/catch rieng.
 *
 * Chay SAU cac ControllerAdvice rieng (Staff, Logistics...) nho @Order thap nhat.
 * - Tra dung ma loi: duong dan khong ton tai -> 404, sai phuong thuc -> 405,
 *   tham so sai/thieu -> 400, tep qua lon -> 413, con lai -> 500.
 * - Request tu trinh duyet nhan trang loi tieng Viet (templates/error.html);
 *   API (/api/**) va request nen nhan chu thuan voi dung ma loi.
 * - Khong dua ex.getMessage() ra ngoai voi loi 500 de khong lo chi tiet ky thuat;
 *   loi 500 duoc ghi log day du de lap trinh vien tra cuu.
 * - AccessDeniedException duoc nem lai de Spring Security xu ly (dang nhap / 403).
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(CloudStorageUnavailableException.class) @ResponseBody
    public ResponseEntity<String> cloudUnavailable(Exception ex) {
        return ResponseEntity.status(503).header("Cache-Control","no-store").body(ex.getMessage());
    }
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class) @ResponseBody
    public ResponseEntity<String> uploadInvalid(org.springframework.web.multipart.MultipartException ex) {
        boolean large=ex instanceof MaxUploadSizeExceededException;
        return ResponseEntity.status(large ? 413 : 400).header("Cache-Control","no-store")
                .body(large ? "Tệp tải lên vượt giới hạn. Hãy chọn tệp nhỏ hơn." : "Không đọc được tệp tải lên. Hãy chọn lại tệp.");
    }

    @ExceptionHandler({IllegalArgumentException.class, org.springframework.validation.BindException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        jakarta.validation.ConstraintViolationException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    @ResponseBody
    public ResponseEntity<String> handleInvalid(Exception ex) {
        return ResponseEntity.badRequest().body("Dữ liệu nhập không hợp lệ hoặc bị thiếu. Vui lòng kiểm tra lại.");
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<String> handleForbidden(Exception ex) {
        return ResponseEntity.status(403).body("Bạn không có quyền thực hiện thao tác này.");
    }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
        org.springframework.dao.PessimisticLockingFailureException.class, org.springframework.dao.DataIntegrityViolationException.class})
    @ResponseBody
    public ResponseEntity<String> handleConflict(Exception ex) {
        return ResponseEntity.status(409).body("Dữ liệu vừa thay đổi hoặc đang được xử lý. Hãy tải lại trang trước khi thử lại.");
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseBody
    public ResponseEntity<String> handleUnavailable(Exception ex) {
        return ResponseEntity.status(503).body("Database chung tạm thời không phản hồi. Vui lòng thử lại sau.");
    }
    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception ex, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (ex instanceof AccessDeniedException denied) throw denied;
        HttpStatus status = statusOf(ex);
        String message = messageOf(status);
        if (status.is5xxServerError()) {
            log.error("Loi xu ly {} {}", request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.debug("Yeu cau khong hop le {} {}: {}", request.getMethod(), request.getRequestURI(), ex.toString());
        }
        if (!wantsHtml(request)) {
            response.setStatus(status.value());
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(message);
            return null;
        }
        var view = new ModelAndView("error", status);
        view.addObject("status", status.value());
        view.addObject("error", status.getReasonPhrase());
        view.addObject("message", message);
        view.addObject("path", request.getRequestURI());
        return view;
    }

    static HttpStatus statusOf(Exception ex) {
        if (ex instanceof MaxUploadSizeExceededException) return HttpStatus.PAYLOAD_TOO_LARGE;
        if (ex instanceof MethodArgumentTypeMismatchException || ex instanceof IllegalArgumentException)
            return HttpStatus.BAD_REQUEST;
        if (ex instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
            if (status != null) return status;
        }
        if (ex instanceof org.springframework.beans.TypeMismatchException
                || ex instanceof org.springframework.http.converter.HttpMessageNotReadableException)
            return HttpStatus.BAD_REQUEST;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    static String messageOf(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Không tìm thấy trang hoặc dữ liệu bạn yêu cầu.";
            case METHOD_NOT_ALLOWED -> "Thao tác này không được hỗ trợ tại đường dẫn hiện tại. Hãy tải lại trang và thử lại.";
            case PAYLOAD_TOO_LARGE -> "Tệp tải lên quá lớn. Hãy chọn tệp nhỏ hơn.";
            case UNSUPPORTED_MEDIA_TYPE, BAD_REQUEST -> "Dữ liệu gửi lên không hợp lệ hoặc bị thiếu. Hãy kiểm tra lại và thử lại.";
            case FORBIDDEN -> "Bạn không có quyền truy cập nội dung này.";
            default -> status.is5xxServerError() ? "Hệ thống đang gặp sự cố. Vui lòng thử lại sau."
                    : "Không thể xử lý yêu cầu.";
        };
    }

    /** Trình duyệt mở trang (Accept có text/html) và không phải API thì trả trang lỗi HTML. */
    static boolean wantsHtml(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String accept = request.getHeader("Accept");
        return !path.startsWith("/api/") && accept != null && accept.contains("text/html");
    }
}
