package vn.edu.hcmute.uteexpress.controller.shipper;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.service.tracking.ShipperNotificationService;

@Controller
@RequestMapping("/shipper/thong-bao")
public class ShipperNotificationController {
    private final ShipperNotificationService service;
    public ShipperNotificationController(ShipperNotificationService service) { this.service = service; }
    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true) // một giao dịch cho cả trang
    public String list(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        model.addAttribute("notifications", service.list(auth.getName(), page));
        model.addAttribute("unread", service.unread(auth.getName()));
        return "shipper/notifications";
    }
    @GetMapping("/chua-doc") @ResponseBody
    public Map<String, Long> unread(Authentication auth) { return Map.of("count", service.unread(auth.getName())); }
    @PostMapping("/{id}/da-doc")
    public String read(@PathVariable Long id, Authentication auth) {
        service.read(id, auth.getName()); return "redirect:/shipper/thong-bao";
    }
    @PostMapping("/doc-tat-ca")
    public String readAll(Authentication auth) { service.readAll(auth.getName()); return "redirect:/shipper/thong-bao"; }
}
