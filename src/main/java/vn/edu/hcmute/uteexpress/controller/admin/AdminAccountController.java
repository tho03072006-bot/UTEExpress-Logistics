package vn.edu.hcmute.uteexpress.controller.admin;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.service.admin.AdminAccountService;

@Controller @RequestMapping("/admin/quan-ly-tai-khoan")
public class AdminAccountController {
    private final AdminAccountService service;
    public AdminAccountController(AdminAccountService service) { this.service = service; }
    @GetMapping public String list(Principal actor, @RequestParam(required = false) AppUser.Role role,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("accounts", service.findAccounts(actor.getName(), role, page));
        model.addAttribute("selectedRole", role); model.addAttribute("roles", AppUser.Role.values());
        return "admin/all-accounts";
    }
    @GetMapping("/tao") public String createForm(Model model) {
        model.addAttribute("form", new AdminAccountCreateRequest()); return "admin/all-account-create";
    }
    @PostMapping("/tao") public String create(Principal actor, @Valid @ModelAttribute("form") AdminAccountCreateRequest form,
            BindingResult errors, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { service.createAccount(actor.getName(), form); form.clearSecrets();
                redirect.addFlashAttribute("success", "Đã cấp tài khoản. Bàn giao mật khẩu riêng; Admin/Manager phải đổi ở lần đăng nhập đầu.");
                return "redirect:/admin/quan-ly-tai-khoan";
            } catch (IllegalArgumentException ex) { errors.reject("invalid", ex.getMessage()); }
            catch (DataIntegrityViolationException ex) { errors.reject("duplicate", "Tên đăng nhập/email vừa được sử dụng. Hãy kiểm tra lại."); }
        }
        form.clearSecrets(); return "admin/all-account-create";
    }
    @GetMapping("/{id}/sua") public String editForm(Principal actor, @PathVariable Long id, Model model) {
        var account = service.findAccount(actor.getName(), id); var form = new AdminAccountUpdateRequest();
        form.setRevision(account.revision()); form.setActive(account.active());
        form.getProfile().setFullName(account.fullName()); form.getProfile().setEmail(account.email());
        form.getProfile().setPhone(account.phone()); form.getProfile().setRole(account.role());
        model.addAttribute("form", form); addAccount(actor, id, model); return "admin/all-account-edit";
    }
    @PostMapping("/{id}/sua") public String edit(Principal actor, @PathVariable Long id,
            @Valid @ModelAttribute("form") AdminAccountUpdateRequest form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { service.updateAccount(actor.getName(), id, form); form.clearSecrets();
                redirect.addFlashAttribute("success", "Đã cập nhật tài khoản; phiên cũ và mã khôi phục đã bị thu hồi.");
                return "redirect:/admin/quan-ly-tai-khoan";
            } catch (IllegalArgumentException ex) { errors.reject("invalid", ex.getMessage()); }
            catch (DataIntegrityViolationException ex) { errors.reject("duplicate", "Email vừa được tài khoản khác sử dụng."); }
        }
        form.clearSecrets(); addAccount(actor, id, model); return "admin/all-account-edit";
    }
    @GetMapping("/{id}/xoa") public String deleteForm(Principal actor, @PathVariable Long id, Model model) {
        var account = service.findAccount(actor.getName(), id); var form = new AdminAccountDeleteRequest();
        form.setRevision(account.revision()); model.addAttribute("form", form); addAccount(actor, id, model);
        return "admin/all-account-delete";
    }
    @PostMapping("/{id}/xoa") public String delete(Principal actor, @PathVariable Long id,
            @Valid @ModelAttribute("form") AdminAccountDeleteRequest form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { service.deleteAccount(actor.getName(), id, form); form.clearSecrets();
                redirect.addFlashAttribute("success", "Đã xóa tài khoản chưa có dữ liệu liên kết. Nhật ký quản trị được giữ lại.");
                return "redirect:/admin/quan-ly-tai-khoan";
            } catch (IllegalArgumentException ex) { errors.reject("invalid", ex.getMessage()); }
            catch (DataIntegrityViolationException ex) { errors.reject("linked", "Tài khoản vừa phát sinh dữ liệu liên kết. Không xóa; hãy tải lại và dùng chức năng khóa."); }
        }
        form.clearSecrets(); addAccount(actor, id, model); return "admin/all-account-delete";
    }
    @GetMapping("/{id}/mat-khau") public String passwordForm(Principal actor, @PathVariable Long id, Model model) {
        var account = service.findAccount(actor.getName(), id); var form = new AdminAccountPasswordRequest();
        form.setRevision(account.revision()); model.addAttribute("form", form); addAccount(actor, id, model);
        return "admin/all-account-password";
    }
    @PostMapping("/{id}/mat-khau") public String password(Principal actor, @PathVariable Long id,
            @Valid @ModelAttribute("form") AdminAccountPasswordRequest form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { service.resetPassword(actor.getName(), id, form); form.clearSecrets();
                redirect.addFlashAttribute("success", "Đã cấp lại mật khẩu, thu hồi phiên cũ. Admin/Manager phải đổi mật khẩu bàn giao.");
                return "redirect:/admin/quan-ly-tai-khoan";
            } catch (IllegalArgumentException ex) { errors.reject("invalid", ex.getMessage()); }
        }
        form.clearSecrets(); addAccount(actor, id, model); return "admin/all-account-password";
    }
    private void addAccount(Principal actor, Long id, Model model) {
        var account = service.findAccount(actor.getName(), id); model.addAttribute("account", account);
        model.addAttribute("allowedRoles", account.isInternal()
            ? new AppUser.Role[]{AppUser.Role.ADMIN, AppUser.Role.MANAGER}
            : new AppUser.Role[]{AppUser.Role.USER, AppUser.Role.SHIPPER});
    }
}
