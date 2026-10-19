package vn.edu.hcmute.momosimulator;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Cả lỗi CSRF/session ở filter cũng có trang giải thích, không lộ path/token hoặc stack trace. */
@Controller
public class SimulatorErrorController implements ErrorController {
    @RequestMapping("/error")
    public String error(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer code && code >= 400 && code <= 599 ? code : 500;
        response.setStatus(status);
        model.addAttribute("notice", status == 403
                ? "Phiên không hợp lệ hoặc hết hạn bảo mật. Hãy quét lại QR từ UTEExpress, không gửi lại biểu mẫu cũ."
                : "Không thể xử lý trang thanh toán này. Hãy kiểm tra kết quả tại UTEExpress và quét QR mới nếu cần.");
        return "momo/home";
    }
}
