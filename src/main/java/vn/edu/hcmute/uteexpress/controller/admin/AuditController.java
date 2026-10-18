package vn.edu.hcmute.uteexpress.controller.admin;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.service.admin.AuditService;
@Controller @RequestMapping("/admin/nhat-ky")
public class AuditController {
    private final AuditService service;
    private final java.time.Clock clock;
    public AuditController(AuditService service, java.time.Clock clock) { this.service = service; this.clock = clock; }
    @GetMapping public String list(@jakarta.validation.Valid @ModelAttribute("filter") vn.edu.hcmute.uteexpress.dto.admin.AuditFilterRequest filter,
            org.springframework.validation.BindingResult errors, @RequestParam(defaultValue = "0") int page,
            Authentication auth, Model model, jakarta.servlet.http.HttpServletResponse response) {
        model.addAttribute("categories", vn.edu.hcmute.uteexpress.dto.admin.AuditFilterRequest.Category.values());
        model.addAttribute("today", java.time.LocalDate.now(clock));
        if (!errors.hasErrors()) {
            try { model.addAttribute("events", filter.isEmpty() ? service.findEvents(auth.getName(), page) : service.findEvents(auth.getName(), filter, page)); }
            catch (IllegalArgumentException ex) { errors.reject("invalid", ex.getMessage()); }
        }
        if (errors.hasErrors()) {
            response.setStatus(400); model.addAttribute("events", org.springframework.data.domain.Page.empty());
        }
        model.addAttribute("invalidFilter", errors.hasErrors());
        return "admin/audit";
    }
}

