package vn.edu.hcmute.uteexpress.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.security.JwtTokenProvider;
import vn.edu.hcmute.uteexpress.service.admin.StaffApiAccessService;

@Controller
public class StaffApiAccessController {
    private final StaffApiAccessService service;
    private final JwtTokenProvider tokens;
    public StaffApiAccessController(StaffApiAccessService service, JwtTokenProvider tokens) { this.service = service; this.tokens = tokens; }

    @GetMapping("/noi-bo/truy-cap-api")
    public String showTokenPage(Model model) {
        model.addAttribute("jwtEnabled", tokens.isEnabled());
        model.addAttribute("tokenLifetimeMinutes", tokens.getLifetimeSeconds() / 60);
        return "admin/api-access";
    }
    @PostMapping("/noi-bo/truy-cap-api")
    public String issue(Principal actor, @Valid @ModelAttribute ApiAccessRequest form, BindingResult errors,
            Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (errors.hasErrors()) model.addAttribute("error", "Nhập mật khẩu hiện tại hợp lệ (tối đa 72 ký tự).");
        else {
            try { model.addAttribute("issuedToken", service.issueToken(actor.getName(), form)); }
            catch (IllegalArgumentException ex) { model.addAttribute("error", ex.getMessage()); }
        }
        // Không đưa mật khẩu trở lại HTML hoặc flash/session.
        model.asMap().remove("apiAccessRequest");
        model.asMap().remove("org.springframework.validation.BindingResult.apiAccessRequest");
        return showTokenPage(model);
    }
    @GetMapping("/admin/an-toan-truy-cap")
    public String security(Principal actor, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("accounts", service.findAccess(actor.getName(), page));
        model.addAttribute("jwtEnabled", tokens.isEnabled());
        model.addAttribute("tokenLifetimeMinutes", tokens.getLifetimeSeconds() / 60);
        return "admin/access-security";
    }
    @PostMapping("/admin/an-toan-truy-cap/{id}/thu-hoi")
    public String revoke(Principal actor, @PathVariable Long id, @Valid @ModelAttribute AccessRevocationRequest form,
            BindingResult errors, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Kiểm tra phiên bản, mật khẩu, lý do thu hồi 5–300 ký tự và xác nhận khi thu hồi chính mình.");
        else {
            try { service.revokeAccess(actor.getName(), id, form); flash.addFlashAttribute("message", "Đã thu hồi toàn bộ phiên web và JWT của tài khoản."); }
            catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        }
        return "redirect:/admin/an-toan-truy-cap";
    }
}
