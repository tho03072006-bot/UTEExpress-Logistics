package vn.edu.hcmute.uteexpress.controller.tracking;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.hcmute.uteexpress.controller.manager.ManagerCodController;
import vn.edu.hcmute.uteexpress.controller.shipper.*;

@ControllerAdvice(assignableTypes = {OrderExtrasController.class, ShipperController.class, ShipperNotificationController.class,
        ShipperOperationsController.class, ShipperCodController.class, ManagerCodController.class})
@org.springframework.core.annotation.Order(0) // luôn chạy trước GlobalExceptionHandler (thấp nhất)
public class LogisticsExceptionHandler {
    @ExceptionHandler(vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException.class)
    public String cloudUnavailable(RuntimeException ex,HttpServletRequest request,HttpServletResponse response,Model model) {
        return error(503,ex.getMessage(),request,response,model);
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public String missing(org.springframework.web.server.ResponseStatusException ex, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        return error(ex.getStatusCode().value(), "Chưa có ảnh bằng chứng hợp lệ hoặc tệp không còn tồn tại.", request, response, model);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public String denied(HttpServletRequest request, HttpServletResponse response, Model model) {
        return error(403, "Bạn không có quyền truy cập vận đơn hoặc hồ sơ này.", request, response, model);
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String invalid(RuntimeException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        return error(400, ex.getMessage(), request, response, model);
    }
    /** Tham số sai kiểu (chữ thay số, trạng thái lạ), thiếu tham số hoặc tệp quá lớn: báo chung, không lộ chi tiết kỹ thuật. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class})
    public String badInput(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        String message = ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException
                ? "Tệp tải lên quá lớn. Ảnh tối đa 5 MB."
                : "Dữ liệu gửi lên không hợp lệ. Hãy kiểm tra bộ lọc, ngày tháng hoặc tải lại trang rồi thử lại.";
        return error(ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException ? 413 : 400, message, request, response, model);
    }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class})
    public String conflict(HttpServletRequest request, HttpServletResponse response, Model model) {
        return error(409, "Dữ liệu đã thay đổi. Hãy tải lại trang trước khi thử lại.", request, response, model);
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public String unavailable(HttpServletRequest request, HttpServletResponse response, Model model) {
        return error(503, "Không kết nối được dữ liệu. Vui lòng thử lại sau.", request, response, model);
    }
    private String error(int status, String message, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setStatus(status);
        model.addAttribute("error", message);
        model.addAttribute("status", status);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        model.addAttribute("backUrl", path.startsWith("/shipper/") ? "/shipper/trang-chu"
                : path.startsWith("/manager/") ? "/manager/trang-chu"
                : path.startsWith("/nguoi-dung/") ? "/nguoi-dung/trang-chu" : "/");
        return "logistics/error";
    }
}
