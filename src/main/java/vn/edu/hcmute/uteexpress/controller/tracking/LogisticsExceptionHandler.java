package vn.edu.hcmute.uteexpress.controller.tracking;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.hcmute.uteexpress.controller.shipper.*;

@ControllerAdvice(assignableTypes = {OrderExtrasController.class, ShipperController.class, ShipperNotificationController.class})
public class LogisticsExceptionHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public String missing(org.springframework.web.server.ResponseStatusException ex, HttpServletResponse response, Model model) {
        return error(ex.getStatusCode().value(), "Chưa có ảnh bằng chứng hợp lệ hoặc tệp không còn tồn tại.", response, model);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public String denied(HttpServletResponse response, Model model) {
        return error(403, "Bạn không có quyền truy cập vận đơn hoặc hồ sơ này.", response, model);
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.web.multipart.MaxUploadSizeExceededException.class})
    public String invalid(Exception ex, HttpServletResponse response, Model model) {
        return error(400, ex instanceof IllegalArgumentException ? ex.getMessage()
                : "Dữ liệu không hợp lệ. Kiểm tra bộ lọc và dung lượng ảnh.", response, model);
    }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class})
    public String conflict(HttpServletResponse response, Model model) {
        return error(409, "Dữ liệu đã thay đổi. Hãy tải lại trang trước khi thử lại.", response, model);
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public String unavailable(HttpServletResponse response, Model model) {
        return error(503, "Không kết nối được dữ liệu. Vui lòng thử lại sau.", response, model);
    }
    private String error(int status, String message, HttpServletResponse response, Model model) {
        response.setStatus(status); model.addAttribute("error", message); return "logistics/error";
    }
}
