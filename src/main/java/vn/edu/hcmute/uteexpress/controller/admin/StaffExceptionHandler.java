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

    @ExceptionHandler(AccessDeniedException.class)
    public String handleForbidden(AccessDeniedException ex, HttpServletResponse response, Model model) {
        return showError(403, "Bạn không có quyền thực hiện thao tác này.", response, model);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public String handleInvalid(Exception ex, HttpServletResponse response, Model model) {
        String message = ex instanceof IllegalArgumentException
                && !(ex instanceof MethodArgumentTypeMismatchException) ? ex.getMessage()
                : "Tham số không hợp lệ hoặc bị thiếu. Vui lòng kiểm tra và tải lại trang.";
        return showError(400, message, response, model);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            DataIntegrityViolationException.class})
    public String handleConflict(Exception ex, HttpServletResponse response, Model model) {
        return showError(409, "Dữ liệu đã thay đổi hoặc đang được xử lý. Hãy tải lại trang trước khi thử lại.", response, model);
    }

    @ExceptionHandler(Exception.class)
    public String handleFailure(Exception ex, HttpServletResponse response, Model model) {
        log.error("Lỗi xử lý chức năng nội bộ: {}", ex.getClass().getSimpleName());
        return showError(500, "Không thể xử lý lúc này. Vui lòng thử lại sau hoặc liên hệ quản trị hệ thống.", response, model);
    }

    private String showError(int status, String message, HttpServletResponse response, Model model) {
        response.setStatus(status);
        model.addAttribute("error", message);
        return "admin/error";
    }
}

