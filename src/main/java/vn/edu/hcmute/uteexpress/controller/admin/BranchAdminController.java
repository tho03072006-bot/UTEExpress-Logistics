package vn.edu.hcmute.uteexpress.controller.admin;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.admin.BranchUpdateRequest;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.service.routing.*;

@Controller @RequestMapping("/admin/chi-nhanh")
public class BranchAdminController {
    private final BranchAdminService service;
    private final RoutingCatalogService catalog;
    public BranchAdminController(BranchAdminService service, RoutingCatalogService catalog) {
        this.service = service; this.catalog = catalog;
    }
    @GetMapping public String list(Principal actor, Model model) {
        model.addAttribute("branches", service.list(actor.getName())); return "admin/branches";
    }
    @GetMapping("/{id}/sua") public String edit(Principal actor, @PathVariable Long id, Model model) {
        var branch = service.get(actor.getName(), id);
        model.addAttribute("form", BranchUpdateRequest.from(branch)); addOptions(actor, id, model); return "admin/branch-edit";
    }
    @PostMapping("/{id}/sua") public String update(Principal actor, @PathVariable Long id,
            @Valid @ModelAttribute("form") BranchUpdateRequest form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.update(actor.getName(), id, form);
                redirect.addFlashAttribute("success", "Đã cập nhật chi nhánh."); return "redirect:/admin/chi-nhanh";
            } catch (IllegalArgumentException | StaffConflictException ex) { errors.reject("invalid", ex.getMessage()); }
        }
        addOptions(actor, id, model); return "admin/branch-edit";
    }
    private void addOptions(Principal actor, Long id, Model model) {
        model.addAttribute("branch", service.get(actor.getName(), id)); model.addAttribute("areas", catalog.areas());
    }
}
