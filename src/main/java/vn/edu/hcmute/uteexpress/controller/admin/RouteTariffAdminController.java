package vn.edu.hcmute.uteexpress.controller.admin;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.admin.RouteTariffUpdateRequest;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.service.routing.RouteTariffAdminService;

@Controller @RequestMapping("/admin/cuoc-tuyen")
public class RouteTariffAdminController {
    private final RouteTariffAdminService service;
    public RouteTariffAdminController(RouteTariffAdminService service) { this.service = service; }
    @GetMapping public String list(Principal actor, Model model) {
        model.addAttribute("tariffs", service.list(actor.getName())); return "admin/route-tariffs";
    }
    @GetMapping("/{id}/sua") public String edit(Principal actor, @PathVariable Long id, Model model) {
        var tariff = service.get(actor.getName(), id);
        model.addAttribute("tariff", tariff); model.addAttribute("form", RouteTariffUpdateRequest.from(tariff));
        return "admin/route-tariff-edit";
    }
    @PostMapping("/{id}/sua") public String update(Principal actor, @PathVariable Long id,
            @Valid @ModelAttribute("form") RouteTariffUpdateRequest form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.update(actor.getName(), id, form);
                redirect.addFlashAttribute("success", "Đã cập nhật cước tuyến, chỉ áp dụng cho đơn mới."); return "redirect:/admin/cuoc-tuyen";
            } catch (IllegalArgumentException | StaffConflictException ex) { errors.reject("invalid", ex.getMessage()); }
        }
        model.addAttribute("tariff", service.get(actor.getName(), id)); return "admin/route-tariff-edit";
    }
}
