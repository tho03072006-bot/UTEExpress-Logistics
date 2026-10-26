package vn.edu.hcmute.uteexpress.controller.admin;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.dto.admin.StaffPasswordChangeRequest;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccountService;

@Controller
@RequestMapping("/noi-bo")
public class StaffAccessController {
    private final StaffAccountService service;
    public StaffAccessController(StaffAccountService service) { this.service = service; }

    @GetMapping("/dang-nhap")
    public String showLogin() { return "admin/login"; }

    /**
     * Đích forward của accessDeniedHandler. Nhận mọi phương thức: thao tác POST bị chặn được forward tới đây với nguyên
     * phương thức POST, nếu chỉ nhận GET thì người dùng thấy lỗi 405 thay cho trang từ chối.
     */
    @RequestMapping("/tu-choi")
    public String showAccessDenied(HttpServletResponse response, Model model) {
        response.setStatus(403);
        model.addAttribute("error", "Bạn không có quyền thực hiện chức năng này, hoặc phiên làm việc đã hết hạn. "
                + "Hãy tải lại trang rồi thử lại; chức năng quản trị chỉ dành cho Admin.");
        return "admin/error";
    }

    @GetMapping("/doi-mat-khau")
    public String showPasswordForm(Model model) {
        model.addAttribute("form", new StaffPasswordChangeRequest());
        return "admin/password-change";
    }

    @PostMapping("/doi-mat-khau")
    public String changePassword(@Valid @ModelAttribute("form") StaffPasswordChangeRequest form,
            BindingResult errors, Principal principal, HttpServletRequest request, Model model) {
        if (!errors.hasErrors()) {
            try {
                service.changePassword(principal.getName(), form);
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) { session.invalidate(); }
                return "redirect:/noi-bo/dang-nhap?changed";
            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        form.clearSecrets();
        return "admin/password-change";
    }
}

