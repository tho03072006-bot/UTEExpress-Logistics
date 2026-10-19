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
    private record SessionCheckout(String token, long expiresAt) implements java.io.Serializable { }

    @GetMapping("/") public String home() { return "momo/home"; }
    @GetMapping("/healthz") @ResponseBody public ResponseEntity<String> health() {
        try { jdbc.queryForObject("SELECT 1", Integer.class); return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("ok"); }
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
            String id = UUID.randomUUID().toString(); checkouts.put(id, new SessionCheckout(token, receipt.expiresAt()));
            // Xóa vé nhạy cảm khỏi thanh địa chỉ; mỗi tab có id riêng trong cùng session.
            return "redirect:/momo/phien/" + id;
        }
    }

    @GetMapping("/momo/phien/{id}")
    public String checkout(@PathVariable String id, HttpServletRequest request, Model model) {
        model.addAttribute("receipt", payments.view(token(request, id))); model.addAttribute("checkoutId", id);
        return "momo/checkout";
    }

    @PostMapping("/momo/phien/{id}/xac-nhan")
    public String confirm(@PathVariable String id, HttpServletRequest request,
            @RequestParam(defaultValue = "false") boolean simulationAcknowledged) {
        if (!simulationAcknowledged) throw new IllegalArgumentException("Cần xác nhận đây là giao dịch mô phỏng.");
        // Không bind amount/orderId/status từ form. Chỉ vé đã xác thực trong session.
        payments.confirm(token(request, id));
        return "redirect:/momo/phien/" + id;
    }

    @PostMapping("/momo/phien/{id}/huy")
    public String cancel(@PathVariable String id, HttpServletRequest request, Model model) {
        payments.view(token(request, id));
        model.addAttribute("notice", "Đã đóng phiên mô phỏng. Trạng thái thanh toán trong database không bị thay đổi. Bạn có thể quét lại QR nếu muốn tiếp tục.");
        checkouts(request.getSession(false)).remove(id); return "momo/home";
    }

    private String token(HttpServletRequest request, String id) {
        if (!id.matches("[a-f0-9-]{36}") || request.getSession(false) == null) throw invalid();
        var checkout = checkouts(request.getSession(false)).get(id);
        if (checkout == null || checkout.expiresAt() <= Instant.now().getEpochSecond()) throw invalid();
        return checkout.token();
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
