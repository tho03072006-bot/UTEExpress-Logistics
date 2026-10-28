package vn.edu.hcmute.uteexpress.controller.admin;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.admin.OperationsPolicyRequest;
import vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService;
@Controller @RequestMapping("/admin/chinh-sach-van-hanh")
public class OperationsPolicyController {
    private final OperationsPolicyService service;
    public OperationsPolicyController(OperationsPolicyService service) { this.service = service; }
    @GetMapping public String edit(Model model) {
        var p = service.current();
        model.addAttribute("form", new OperationsPolicyRequest(p.getRevision(), p.getPickupHours(), p.getStandardHours(),
                p.getExpressHours(), p.getSuperExpressHours(), p.getMaxDeliveryAttempts(), p.getComplaintResponseHours(), p.getComplaintResolveHours(), p.getMaxActiveOrders(), p.getDropOffDiscount().intValueExact(), ""));
        return "admin/operations-policy";
    }
    @PostMapping public String save(@Valid @ModelAttribute("form") OperationsPolicyRequest form, BindingResult errors,
            Authentication auth, RedirectAttributes flash) {
        if (errors.hasErrors()) return "admin/operations-policy";
        try { service.update(auth.getName(), form); }
        catch (IllegalArgumentException ex) { errors.reject("policy", ex.getMessage()); return "admin/operations-policy"; }
        flash.addFlashAttribute("message", "Đã cập nhật chính sách vận hành."); return "redirect:/admin/chinh-sach-van-hanh";
    }
}
