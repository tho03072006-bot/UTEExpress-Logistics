package vn.edu.hcmute.uteexpress.controller.user;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.MomoCheckout;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentResult;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.service.MomoPaymentService;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.PaymentQrCodeService;

import java.util.Map;

@Controller
@RequestMapping("/nguoi-dung/thanh-toan")
public class PaymentController {
    private final OrderPaymentService paymentService;
    private final MomoPaymentService momoService;
    private final PaymentQrCodeService qrService;

    public PaymentController(OrderPaymentService paymentService, MomoPaymentService momoService, PaymentQrCodeService qrService) {
        this.paymentService = paymentService;
        this.momoService = momoService;
        this.qrService = qrService;
    }

    @GetMapping("/{orderId}")
    public String paymentGateway(@PathVariable Long orderId, Authentication authentication, HttpSession session,
                                 Model model, RedirectAttributes redirectAttributes) {
        try {
            OrderPayment payment = paymentService.findOwnedPayment(orderId, authentication.getName());
            model.addAttribute("payment", payment);
            model.addAttribute("order", payment.getOrder());
            model.addAttribute("momoEnabled", momoService.isEnabled());
            Object saved = session.getAttribute(sessionKey(orderId));
            if (saved instanceof MomoCheckout checkout && payment.getTransactionRef() != null
                    && payment.getTransactionRef().startsWith(checkout.reference())
                    && payment.getStatus() == OrderPayment.PaymentStatus.UNPAID) {
                model.addAttribute("checkout", checkout);
                if (!checkout.qrContent().isBlank()) model.addAttribute("qrSvg", qrService.toSvg(checkout.qrContent()));
            }
            return "user/payment-gateway";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/nguoi-dung/don-hang";
        }
    }

    @PostMapping("/{orderId}/momo")
    public String startHosted(@PathVariable Long orderId, Authentication authentication, HttpSession session,
                              RedirectAttributes redirectAttributes) {
        return start(orderId, authentication, session, redirectAttributes, false);
    }

    @PostMapping("/{orderId}/momo-qr")
    public String startQr(@PathVariable Long orderId, Authentication authentication, HttpSession session,
                          RedirectAttributes redirectAttributes) {
        return start(orderId, authentication, session, redirectAttributes, true);
    }

    private String start(Long orderId, Authentication auth, HttpSession session, RedirectAttributes redirects, boolean qr) {
        try {
            if (!momoService.isEnabled()) {
                throw new IllegalStateException("Thanh toán MoMo TEST chưa sẵn sàng. Vui lòng thử lại sau.");
            }
            OrderPayment owned = paymentService.findOwnedPayment(orderId, auth.getName());
            Object saved = session.getAttribute(sessionKey(orderId));
            if (owned.getStatus() == OrderPayment.PaymentStatus.UNPAID && owned.getTransactionRef() != null
                    && saved instanceof MomoCheckout checkout && owned.getTransactionRef().equals(checkout.reference())
                    && owned.getOrder().getStatus() != vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.CANCELLED
                    && owned.getOrder().getStatus() != vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.RETURNED) {
                return qr ? gatewayRedirect(orderId) : "redirect:" + checkout.payUrl();
            }
            MomoCheckout checkout = momoService.start(orderId, auth.getName(), qr);
            session.setAttribute(sessionKey(orderId), checkout);
            return qr ? gatewayRedirect(orderId) : "redirect:" + checkout.payUrl();
        } catch (IllegalStateException ex) {
            redirects.addFlashAttribute("error", ex.getMessage());
            return gatewayRedirect(orderId);
        }
    }

    @PostMapping({"/{orderId}/momo/kiem-tra", "/{orderId}/xac-nhan"})
    public String check(@PathVariable Long orderId, Authentication auth, RedirectAttributes redirects) {
        try {
            MomoPaymentResult result = momoService.check(orderId, auth.getName());
            redirects.addFlashAttribute(result.state() == MomoPaymentResult.State.PAID ? "message" : "error", result.message());
        } catch (IllegalStateException ex) {
            redirects.addFlashAttribute("error", ex.getMessage());
        }
        return gatewayRedirect(orderId);
    }

    @PostMapping("/{orderId}/momo/trang-thai")
    @ResponseBody
    public ResponseEntity<?> status(@PathVariable Long orderId, Authentication auth) {
        try { return ResponseEntity.ok(momoService.check(orderId, auth.getName())); }
        catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("state", "ERROR", "message", ex.getMessage()));
        }
    }

    private static String sessionKey(Long orderId) { return "ute.momo.checkout." + orderId; }
    private static String gatewayRedirect(Long orderId) { return "redirect:/nguoi-dung/thanh-toan/" + orderId; }
}
