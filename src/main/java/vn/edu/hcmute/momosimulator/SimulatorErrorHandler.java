package vn.edu.hcmute.momosimulator;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;

@ControllerAdvice(basePackageClasses = SimulatorController.class)
public class SimulatorErrorHandler {
    @ExceptionHandler(vn.edu.hcmute.uteexpress.service.payment.ReplacedMomoQrException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String replaced(Exception exception, Model model) {
        model.addAttribute("replacedQr", true); model.addAttribute("notice", exception.getMessage());
        return "momo/home";
    }
    /** Người khác đã trả đơn này bằng mã QR khác: báo rõ, không hiện nút thanh toán. */
    @ExceptionHandler(vn.edu.hcmute.uteexpress.service.payment.PaidByAnotherQrException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String paidElsewhere(Exception exception, Model model) {
        model.addAttribute("alreadyPaid", true);
        model.addAttribute("notice", exception.getMessage());
        return "momo/home";
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(Exception exception, Model model) {
        model.addAttribute("notice", "Liên kết/phiên thanh toán không còn hợp lệ, tài khoản bị khóa hoặc đơn đã thay đổi. Hãy kiểm tra kết quả tại UTEExpress và mở lại thanh toán nếu cần.");
        return "momo/home";
    }
    @ExceptionHandler(DataAccessException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String unavailable(Model model) {
        model.addAttribute("notice", "Chưa thể xác minh kết quả với Aiven. Không bấm liên tục; hãy kiểm tra trạng thái ở UTEExpress rồi thử lại. Hệ thống không báo thành công khi chưa xác nhận từ database.");
        return "momo/home";
    }
}
