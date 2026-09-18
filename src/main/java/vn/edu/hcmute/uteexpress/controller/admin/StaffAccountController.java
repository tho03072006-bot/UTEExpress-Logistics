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
import vn.edu.hcmute.uteexpress.service.admin.StaffAccountService;

@Controller
@RequestMapping("/admin/tai-khoan")
public class StaffAccountController {
    private final StaffAccountService service;
    public StaffAccountController(StaffAccountService service) { this.service = service; }

    @GetMapping
    public String listAccounts(@RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        model.addAttribute("accounts", service.findAccounts(principal.getName(), page));
        return "admin/accounts";
    }

    @GetMapping("/cap-moi")
    public String showCreateForm(Model model) {
        model.addAttribute("form", new StaffAccountCreateRequest());
        return "admin/account-create";
    }

    @PostMapping("/cap-moi")
    public String createAccount(@Valid @ModelAttribute("form") StaffAccountCreateRequest form,
            BindingResult errors, Principal principal, RedirectAttributes redirect, Model model) {
        if (!errors.hasErrors()) {
            try {
                service.createAccount(principal.getName(), form);
                redirect.addFlashAttribute("success", "Đã cấp tài khoản. Bàn giao mật khẩu qua kênh riêng; người nhận phải đổi mật khẩu khi đăng nhập.");
                return "redirect:/admin/tai-khoan";
            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
            } catch (DataIntegrityViolationException ex) {
                model.addAttribute("error", "Tên đăng nhập hoặc email vừa được sử dụng. Hãy kiểm tra và thử lại.");
            }
        }
        form.clearSecrets();
        return "admin/account-create";
    }

    @PostMapping("/{id}/cap-nhat")
    public String updateAccount(@PathVariable Long id,
            @Valid @ModelAttribute StaffAccountUpdateRequest form, BindingResult errors,
            Principal principal, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Dữ liệu cấp quyền không hợp lệ. Hãy tải lại danh sách.");
        } else {
            try {
                service.updateAccount(principal.getName(), id, form);
                redirect.addFlashAttribute("success", "Đã cập nhật quyền và trạng thái; các phiên cũ đã bị thu hồi.");
            } catch (IllegalArgumentException ex) {
                redirect.addFlashAttribute("error", ex.getMessage());
            }
        }
        return "redirect:/admin/tai-khoan";
    }

    @PostMapping("/{id}/cap-lai-mat-khau")
    public String resetPassword(@PathVariable Long id, @RequestParam long revision,
            @Valid @ModelAttribute StaffPasswordRequest form, BindingResult errors,
            Principal principal, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Mật khẩu không hợp lệ: cần 12–64 ký tự, chữ hoa/thường, số, ký tự đặc biệt, tối đa 72 byte và xác nhận trùng khớp.");
        } else {
            try {
                service.resetPassword(principal.getName(), id, revision, form);
                redirect.addFlashAttribute("success", "Đã cấp lại mật khẩu và thu hồi phiên cũ. Người nhận phải đổi mật khẩu sau khi đăng nhập.");
            } catch (IllegalArgumentException ex) {
                redirect.addFlashAttribute("error", ex.getMessage());
            }
        }
        form.clearSecrets();
        return "redirect:/admin/tai-khoan";
    }
}

