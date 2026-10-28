package vn.edu.hcmute.uteexpress.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@ControllerAdvice(basePackages = {
        "vn.edu.hcmute.uteexpress.controller.admin", "vn.edu.hcmute.uteexpress.controller.manager"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StaffExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(StaffExceptionHandler.class);
    @ExceptionHandler(vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException.class)
    public String cloudUnavailable(Exception ex, HttpServletResponse response, Model model) {
        return showError(503, ex.getMessage(), response, model);
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public String handleStatus(org.springframework.web.server.ResponseStatusException ex,HttpServletResponse response,Model model) {
        return showError(ex.getStatusCode().value(),"Tệp hoặc hồ sơ không còn khả dụng. Hãy tải lại trang và kiểm tra.",response,model);
    }
    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public String handleUpload(Exception ex,HttpServletResponse response,Model model) {
        boolean large=ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException;
        return showError(large ? 413 : 400,large ? "Tệp tải lên vượt giới hạn. Ảnh chứng từ tối đa 5 MB."
                : "Tệp tải lên bị thiếu hoặc không đọc được. Hãy chọn lại tệp.",response,model);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public String handleMethodNotAllowed(Exception ex, HttpServletResponse response, Model model) {
        return showError(405, "Chức năng này không hỗ trợ thao tác vừa gửi. Hãy mở lại trang và thử đúng chức năng.", response, model);
    }

    /** Lý do do service nêu (ví dụ "Bạn chỉ thao tác được kho của chi nhánh mình.") rõ hơn câu chung; câu mặc định của Spring thì thay. */
    @ExceptionHandler(AccessDeniedException.class)
    public String handleForbidden(AccessDeniedException ex, HttpServletResponse response, Model model) {
        String reason = ex.getMessage();
        boolean specific = reason != null && !reason.isBlank() && !reason.equals("Access Denied");
        return showError(403, specific ? reason : "Bạn không có quyền thực hiện thao tác này.", response, model);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, org.springframework.validation.BindException.class,
            jakarta.validation.ConstraintViolationException.class})
    public String handleInvalid(Exception ex, HttpServletResponse response, Model model) {
        String message = ex instanceof IllegalArgumentException
                && !(ex instanceof MethodArgumentTypeMismatchException) ? ex.getMessage()
                : "Tham số không hợp lệ hoặc bị thiếu. Vui lòng kiểm tra và tải lại trang.";
        return showError(400, message, response, model);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            DataIntegrityViolationException.class, vn.edu.hcmute.uteexpress.exception.StaffConflictException.class})
    public String handleConflict(Exception ex, HttpServletResponse response, Model model) {
        return showError(409, "Dữ liệu đã thay đổi hoặc đang được xử lý. Hãy tải lại trang trước khi thử lại.", response, model);
    }

    @ExceptionHandler(Exception.class)
    public String handleFailure(Exception ex, HttpServletResponse response, Model model) {
        log.error("Lỗi xử lý chức năng nội bộ: {}", ex.getClass().getSimpleName());
        return showError(500, "Không thể xử lý lúc này. Vui lòng thử lại sau hoặc liên hệ quản trị hệ thống.", response, model);
    }

    @ExceptionHandler(DataAccessException.class)
    public String handleDatabase(Exception ex, HttpServletResponse response, Model model) {
        return showError(503, "Database chung tạm thời không phản hồi. Không tự chuyển sang database local; hãy thử lại sau.", response, model);
    }

    private String showError(int status, String message, HttpServletResponse response, Model model) {
        response.setStatus(status);
        model.addAttribute("error", message);
        return "admin/error";
    }
}

