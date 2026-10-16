package vn.edu.hcmute.uteexpress.controller.admin;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.service.admin.AuditService;
@Controller @RequestMapping("/admin/nhat-ky")
public class AuditController {
    private final AuditService service;
    public AuditController(AuditService service) { this.service = service; }
    @GetMapping public String list(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        model.addAttribute("events", service.findEvents(auth.getName(), page));
        return "admin/audit";
    }
}

