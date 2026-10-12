package vn.edu.hcmute.uteexpress.controller.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.hcmute.uteexpress.dto.PasswordResetRequest;
import vn.edu.hcmute.uteexpress.service.AuthService;
import vn.edu.hcmute.uteexpress.util.AuthInputRules;
import vn.edu.hcmute.uteexpress.util.OtpGuard;
import java.time.Duration;

/**
 * Chuc nang chung: dang ky (+ OTP email), dang nhap, dang xuat, quen mat khau (+ OTP email).
 * Dang nhap/dang xuat do Spring Security formLogin tu xu ly (xem SecurityConfig),
 * khong can viet Controller rieng cho 2 chuc nang do.
 */
@Controller
public class AuthController {

    private final AuthService authService;
    private final OtpGuard sourceGuard = new OtpGuard();

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/dang-nhap")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/dang-ky")
    public String registerPage(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/dang-ky")
    public String register(@Validated @ModelAttribute("form") RegisterForm form, BindingResult bindingResult,
                            Model model, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        if (!allowSource(request, "send", 10)) {
            model.addAttribute("error", "Có quá nhiều yêu cầu gửi mã. Vui lòng thử lại sau một phút.");
            return "auth/register";
        }
        try {
            authService.register(form.getUsername(), form.getPassword(), form.getEmail(), form.getFullName());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/register";
        }
        model.addAttribute("username", form.getUsername());
        return "auth/verify-otp";
    }

    @PostMapping("/xac-thuc-otp")
    public String verifyOtp(@RequestParam String username, @RequestParam String otp, Model model,
                            HttpServletRequest request) {
        boolean valid = allowSource(request, "verify", 30) && authService.verifyOtp(username, otp);
        if (!valid) {
            model.addAttribute("username", username);
            model.addAttribute("error", "Mã OTP không đúng, hết hạn hoặc đã vượt giới hạn thử. Vui lòng gửi lại mã.");
            return "auth/verify-otp";
        }
        model.addAttribute("activated", true);
        return "auth/login";
    }

    @PostMapping("/xac-thuc-otp/gui-lai")
    public String resendActivation(@RequestParam String username, Model model, HttpServletRequest request) {
        if (allowSource(request, "send", 10)) {
            try {
                authService.resendActivationOtp(username);
            } catch (IllegalStateException ex) {
                // Same response for an unknown/enabled account, cooldown, or SMTP failure.
            }
        }
        model.addAttribute("username", username);
        model.addAttribute("message", "Nếu tài khoản đang chờ kích hoạt, mã mới sẽ được gửi tới email đã đăng ký. Mỗi lần gửi cần cách nhau ít nhất 60 giây.");
        return "auth/verify-otp";
    }

    // ===================== Quen mat khau =====================

    @GetMapping("/quen-mat-khau")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    /**
     * Gui ma OTP dat lai mat khau. Du email co ton tai hay khong thi man hinh tiep theo
     * van giong het nhau (xem AuthService.sendPasswordResetOtp) - tranh de nguoi la do duoc
     * email nao dang co tai khoan trong he thong.
     */
    @PostMapping("/quen-mat-khau")
    public String sendResetOtp(@RequestParam String email, Model model, HttpServletRequest request) {
        try {
            email = AuthInputRules.email(email);
        } catch (IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("email", email);
            return "auth/forgot-password";
        }
        try {
            if (allowSource(request, "send", 10)) authService.sendPasswordResetOtp(email);
        } catch (IllegalStateException ex) {
            // Giữ cùng phản hồi cho email tồn tại/không tồn tại để tránh dò tài khoản.
        }

        PasswordResetRequest form = new PasswordResetRequest();
        form.setEmail(email);
        model.addAttribute("form", form);
        model.addAttribute("otpJustSent", true);
        return "auth/reset-password";
    }

    /** Mo thang trang nhap ma, danh cho nguoi da co ma trong mail nhung lo dong trinh duyet. */
    @GetMapping("/dat-lai-mat-khau")
    public String resetPasswordPage(Model model) {
        model.addAttribute("form", new PasswordResetRequest());
        return "auth/reset-password";
    }

    @PostMapping("/dat-lai-mat-khau")
    public String resetPassword(@Valid @ModelAttribute("form") PasswordResetRequest form,
                                BindingResult bindingResult, Model model, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }
        if (!form.isPasswordConfirmed()) {
            model.addAttribute("error", "Hai ô mật khẩu không khớp nhau.");
            return "auth/reset-password";
        }

        boolean done = allowSource(request, "verify", 30)
                && authService.resetPassword(form.getEmail(), form.getOtp(), form.getNewPassword());
        if (!done) {
            model.addAttribute("error", "Mã OTP không đúng hoặc đã hết hạn. Vui lòng gửi lại mã mới.");
            return "auth/reset-password";
        }

        model.addAttribute("passwordReset", true);
        return "auth/login";
    }

    private boolean allowSource(HttpServletRequest request, String operation, int limit) {
        // Do not trust a client-supplied X-Forwarded-For header to bypass this limit.
        return sourceGuard.take(operation + ":" + request.getRemoteAddr(), limit, Duration.ofMinutes(1)) >= 0;
    }


    /** Du lieu form dang ky lay tu giao dien - khong dung truc tiep Entity AppUser de nhan du lieu. */
    public static class RegisterForm {

        @NotBlank(message = "Vui lòng nhập tên đăng nhập")
        @Pattern(regexp = AuthInputRules.USERNAME_PATTERN,
                message = "Tên đăng nhập cần 3–50 ký tự chữ, số, chấm, gạch dưới hoặc gạch ngang")
        private String username;

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Size(min = 6, max = 72, message = "Mật khẩu cần từ 6 đến 72 ký tự")
        private String password;

        @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        private String email;

        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "Họ tên không được chứa ký tự điều khiển")
        private String fullName;

        @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8")
        public boolean isPasswordWithinByteLimit() {
            return AuthInputRules.passwordWithinByteLimit(password);
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = AuthInputRules.trim(username);
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = AuthInputRules.normalizeEmail(email);
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = AuthInputRules.trim(fullName);
        }
    }
}
