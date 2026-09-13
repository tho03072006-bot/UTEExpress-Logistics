package vn.edu.hcmute.uteexpress.controller.user;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;

/**
 * Thanh toán cước vận chuyển - TV1 phụ trách (việc 3).
 *
 * COD chạy thật: trạng thái thanh toán lưu trong CSDL, tiền thu lúc giao hàng.
 * VNPay / MoMo là trang GIẢ LẬP phục vụ demo - đồ án môn học không có API key thật của
 * cổng thanh toán, nên toàn bộ luồng chạy nội bộ, không có request nào ra Internet
 * và không hề đụng tới tiền thật.
 */
@Controller
@RequestMapping("/nguoi-dung/thanh-toan")
public class PaymentController {

    private final OrderPaymentService orderPaymentService;

    public PaymentController(OrderPaymentService orderPaymentService) {
        this.orderPaymentService = orderPaymentService;
    }

    @GetMapping("/{orderId}")
    public String paymentGateway(@PathVariable Long orderId, Authentication authentication,
                                 Model model, RedirectAttributes redirectAttributes) {
        try {
            OrderPayment payment = orderPaymentService.findOwnedPayment(orderId, authentication.getName());
            model.addAttribute("payment", payment);
            model.addAttribute("order", payment.getOrder());
            return "user/payment-gateway";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/nguoi-dung/don-hang";
        }
    }

    @PostMapping("/{orderId}/xac-nhan")
    public String confirmPayment(@PathVariable Long orderId, Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            OrderPayment payment = orderPaymentService.confirmWalletPayment(orderId, authentication.getName());
            redirectAttributes.addFlashAttribute("message",
                    "Thanh toán thành công đơn " + payment.getOrder().getTrackingCode()
                            + ". Mã giao dịch mô phỏng: " + payment.getTransactionRef());
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/nguoi-dung/don-hang";
    }
}
