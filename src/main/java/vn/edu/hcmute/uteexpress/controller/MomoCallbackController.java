package vn.edu.hcmute.uteexpress.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.service.MomoPaymentService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Only signed server IPN may settle anonymously. Return parameters are navigation hints. */
@Controller
@RequestMapping("/thanh-toan/momo")
public class MomoCallbackController {
    private final MomoPaymentService momoService;
    public MomoCallbackController(MomoPaymentService momoService) { this.momoService = momoService; }

    @GetMapping("/ket-qua")
    public String browserReturn(@RequestParam Map<String, String> params, RedirectAttributes redirects) {
        try {
            Long orderId = momoService.orderIdFromReference(params.get("orderId"));
            redirects.addFlashAttribute("momoReturned", true);
            return "redirect:/nguoi-dung/thanh-toan/" + orderId;
        } catch (IllegalStateException ex) {
            redirects.addFlashAttribute("error", "Không xác định được giao dịch MoMo. Vui lòng kiểm tra trong đơn hàng của bạn.");
            return "redirect:/nguoi-dung/don-hang";
        }
    }

    @PostMapping("/ipn")
    @ResponseBody
    public ResponseEntity<Void> ipn(@RequestBody Map<String, Object> body) {
        Map<String, String> params = new LinkedHashMap<>();
        body.forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
        try {
            momoService.handleIpn(params);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
