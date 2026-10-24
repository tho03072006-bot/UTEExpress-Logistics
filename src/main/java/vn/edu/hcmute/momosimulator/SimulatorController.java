package vn.edu.hcmute.momosimulator;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.service.payment.MomoPaymentService;

@Controller
public class SimulatorController {
    private static final String CHECKOUTS = SimulatorController.class.getName() + ".checkouts";
    private final MomoPaymentService payments;
    private final JdbcTemplate jdbc;
    public SimulatorController(MomoPaymentService payments, JdbcTemplate jdbc) { this.payments = payments; this.jdbc = jdbc; }
    /** paidHere = trình duyệt này đã bấm xác nhận và chính lần bấm đó ghi nhận thanh toán. */
    private record SessionCheckout(String token, long expiresAt, boolean paidHere) implements java.io.Serializable { }

    @GetMapping("/") public String home() { return "momo/home"; }
    @GetMapping("/healthz") @ResponseBody public ResponseEntity<String> health() {
        try { jdbc.queryForObject("SELECT 1", Integer.class); return ResponseEntity.ok().header("X-Momo-Protocol", Integer.toString(vn.edu.hcmute.uteexpress.service.payment.MomoTicketCodec.PROTOCOL_VERSION)).cacheControl(CacheControl.noStore()).body("ok"); }
        catch (org.springframework.dao.DataAccessException exception) { return ResponseEntity.status(503).body("unavailable"); }
    }

    @GetMapping("/momo/thanh-toan")
    public String open(@RequestParam(name = "t") String token, HttpServletRequest request) {
        var receipt = payments.view(token);
        var checkouts = checkouts(request.getSession());
        synchronized (checkouts) {
            checkouts.values().removeIf(item -> item.expiresAt() <= Instant.now().getEpochSecond());
            for (var entry : checkouts.entrySet()) if (entry.getValue().token().equals(token)) return "redirect:/momo/phien/" + entry.getKey();
            if (checkouts.size() >= 5) throw new IllegalArgumentException("Bạn đã mở nhiều phiên thanh toán. Hãy đóng phiên cũ hoặc đợi 10 phút.");
            String id = UUID.randomUUID().toString(); checkouts.put(id, new SessionCheckout(token, receipt.expiresAt(), false));
            // Xóa vé nhạy cảm khỏi thanh địa chỉ; mỗi tab có id riêng trong cùng session.
            return "redirect:/momo/phien/" + id;
        }
    }

    @GetMapping("/momo/phien/{id}")
    public String checkout(@PathVariable String id, HttpServletRequest request, Model model) {
        var checkout = checkout(request, id);
        model.addAttribute("receipt", payments.view(checkout.token())); model.addAttribute("checkoutId", id);
        // Hai điện thoại quét cùng một QR: chỉ máy đã trả thấy "Thanh toán thành công", máy kia thấy đơn đã được trả.
        model.addAttribute("paidHere", checkout.paidHere());
        return "momo/checkout";
    }

    public record CheckoutState(String state, String message) { }
    /** Chỉ phiên trong cookie hiện tại; không nhận id đơn, số tiền hay vé do trình duyệt tự thay. */
    @GetMapping(value = "/momo/phien/{id}/trang-thai", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<CheckoutState> state(@PathVariable String id, HttpServletRequest request) {
        try {
            var checkout = checkout(request, id);
            var receipt = payments.view(checkout.token());
            String state = receipt.paid() ? (checkout.paidHere() ? "PAID" : "ALREADY_PAID") : "UNPAID";
            return stateResponse(HttpStatus.OK, state, receipt.paid() ? "Đơn đã được thanh toán, không thu thêm tiền." : "Mã QR đang có hiệu lực.");
        } catch (vn.edu.hcmute.uteexpress.service.payment.ReplacedMomoQrException exception) {
            return stateResponse(HttpStatus.CONFLICT, "REPLACED", exception.getMessage());
        } catch (vn.edu.hcmute.uteexpress.service.payment.PaidByAnotherQrException exception) {
            return stateResponse(HttpStatus.CONFLICT, "ALREADY_PAID", exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return stateResponse(HttpStatus.GONE, "UNAVAILABLE", "Phiên không còn hợp lệ, đã hết hạn hoặc đơn đã thay đổi. Hãy mở lại mã QR mới nhất.");
        } catch (org.springframework.dao.DataAccessException exception) {
            return stateResponse(HttpStatus.SERVICE_UNAVAILABLE, "RETRY", "Chưa kết nối được database để xác minh. Không ghi nhận thanh toán mới.");
        }
    }
    private static ResponseEntity<CheckoutState> stateResponse(HttpStatus status, String state, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new CheckoutState(state, message));
    }

    @PostMapping("/momo/phien/{id}/xac-nhan")
    public String confirm(@PathVariable String id, HttpServletRequest request,
            @RequestParam(defaultValue = "false") boolean simulationAcknowledged) {
        if (!simulationAcknowledged) throw new IllegalArgumentException("Cần xác nhận đây là giao dịch mô phỏng.");
        // Không bind amount/orderId/status từ form. Chỉ vé đã xác thực trong session.
        var checkout = checkout(request, id);
        if (payments.confirmOnce(checkout.token()).paidNow())
            checkouts(request.getSession(false)).put(id, new SessionCheckout(checkout.token(), checkout.expiresAt(), true));
        return "redirect:/momo/phien/" + id;
    }

    @PostMapping("/momo/phien/{id}/huy")
    public String cancel(@PathVariable String id, HttpServletRequest request, Model model) {
        checkout(request, id);
        model.addAttribute("notice", "Đã đóng phiên mô phỏng. Trạng thái thanh toán trong database không bị thay đổi. Bạn có thể quét lại QR nếu muốn tiếp tục.");
        checkouts(request.getSession(false)).remove(id); return "momo/home";
    }

    private SessionCheckout checkout(HttpServletRequest request, String id) {
        if (!id.matches("[a-f0-9-]{36}") || request.getSession(false) == null) throw invalid();
        var checkout = checkouts(request.getSession(false)).get(id);
        if (checkout == null || checkout.expiresAt() <= Instant.now().getEpochSecond()) throw invalid();
        return checkout;
    }
    @SuppressWarnings("unchecked")
    private Map<String, SessionCheckout> checkouts(HttpSession session) {
        synchronized (session) {
            if (session.getAttribute(CHECKOUTS) == null) session.setAttribute(CHECKOUTS, new ConcurrentHashMap<String, SessionCheckout>());
            return (Map<String, SessionCheckout>) session.getAttribute(CHECKOUTS);
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Phiên đã hết hạn hoặc không thuộc trình duyệt này. Hãy quét QR lại từ UTEExpress."); }
}
