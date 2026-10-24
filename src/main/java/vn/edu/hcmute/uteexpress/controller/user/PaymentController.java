package vn.edu.hcmute.uteexpress.controller.user;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.payment.*;

/** Web local chỉ tạo QR/đọc trạng thái; không còn nút tự xác nhận đã trả tiền. */
@Controller
public class PaymentController {
    private final OrderPaymentService payments;
    private final MomoPaymentService momo;
    private final MomoStartupService startup;
    public PaymentController(OrderPaymentService payments, MomoPaymentService momo, MomoStartupService startup) {
        this.payments = payments; this.momo = momo; this.startup = startup;
    }

    @GetMapping("/nguoi-dung/thanh-toan/{orderId}")
    public String paymentGateway(@PathVariable long orderId, Authentication authentication,
            Model model, RedirectAttributes redirect, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Referrer-Policy", "no-referrer");
        try {
            if (orderId <= 0) throw new IllegalArgumentException("Mã đơn không hợp lệ.");
            var payment = payments.findOwnedPayment(orderId, authentication.getName());
            model.addAttribute("payment", payment); model.addAttribute("order", payment.getOrder());
            if (payment.getMethod() == OrderPayment.PaymentMethod.MOMO
                    && payment.getStatus() == OrderPayment.PaymentStatus.UNPAID && !WalletPaymentRules.isClosed(payment.getOrder())) {
                try {
                    model.addAttribute("checkout", momo.createCheckout(orderId, authentication.getName()));
                    // Render Free có thể đang ngủ: đánh thức ở nền ngay khi hiện QR, trước lúc khách kịp quét.
                    startup.wakeForPayment();
                }
                catch (IllegalStateException exception) { model.addAttribute("checkoutError", exception.getMessage()); }
            }
            model.addAttribute("momoStartup", startup.snapshot());
            return "user/payment-gateway";
        } catch (IllegalStateException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", "Không thể mở thanh toán. Hãy chọn lại đơn hàng của bạn.");
            return "redirect:/nguoi-dung/don-hang";
        }
    }

    @PostMapping("/nguoi-dung/thanh-toan/{orderId}/doi-sang-momo")
    public String switchLegacy(@PathVariable long orderId, Authentication authentication, RedirectAttributes redirect) {
        try { momo.switchLegacyPayment(orderId, authentication.getName()); }
        catch (IllegalArgumentException | IllegalStateException exception) { redirect.addFlashAttribute("error", exception.getMessage()); }
        return "redirect:/nguoi-dung/thanh-toan/" + orderId;
    }

    @GetMapping("/api/payments/momo/{orderId}/status") @ResponseBody
    public ResponseEntity<?> status(@PathVariable long orderId, Authentication authentication) {
        try {
            var payment = momo.status(orderId, authentication.getName());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PaymentStatusResponse(
                    payment.state(), payment.message(), payment.transactionRef(), payment.paidAt(), startup.snapshot()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore())
                    .body(java.util.Map.of("message", "Không thể truy cập thanh toán này."));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                    .body(java.util.Map.of("state", "UNAVAILABLE", "message",
                            "Thông tin cước hoặc biên nhận chưa hợp lệ. Vui lòng liên hệ Manager để kiểm tra."));
        }
    }
    public record PaymentStatusResponse(String state, String message, String transactionRef, String paidAt,
                                        MomoStartupService.Snapshot render) { }
}
