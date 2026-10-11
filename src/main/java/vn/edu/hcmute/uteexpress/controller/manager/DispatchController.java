package vn.edu.hcmute.uteexpress.controller.manager;

import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.service.tracking.DispatchService;

@Controller
@RequestMapping("/manager/phan-cong")
public class DispatchController {
    private final DispatchService service;
    public DispatchController(DispatchService service) { this.service = service; }
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int shipperPage, Authentication auth, Model model) {
        model.addAttribute("orders", service.pending(auth.getName(), page));
        model.addAttribute("shippers", service.shippers(auth.getName(), shipperPage));
        return "manager/dispatch";
    }
    @PostMapping
    public String assign(@RequestParam String trackingCode, @RequestParam Long shipperId,
            @RequestParam long expectedShipperId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedUpdatedAt,
            @RequestParam String note, Authentication auth, RedirectAttributes flash) {
        try {
            service.assign(trackingCode, shipperId, expectedShipperId, expectedUpdatedAt, note, auth.getName());
            flash.addFlashAttribute("message", "Đã phân công và gửi thông báo cho Shipper.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/manager/phan-cong";
    }
}
