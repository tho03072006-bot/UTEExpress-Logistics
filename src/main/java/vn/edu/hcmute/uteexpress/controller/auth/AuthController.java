package vn.edu.hcmute.uteexpress.controller.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import vn.edu.hcmute.uteexpress.validation.PublicAccountValidation;
import vn.edu.hcmute.uteexpress.validation.ValidPublicPassword;
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

/**
 * Chuc nang chung: dang ky (+ OTP email), dang nhap, dang xuat, quen mat khau (+ OTP email).
 * Dang nhap/dang xuat do Spring Security formLogin tu xu ly (xem SecurityConfig),
 * khong can viet Controller rieng cho 2 chuc nang do.
 */
@Controller
public class AuthController {

    private final AuthService authService;

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
                            Model model) {
        if (bindingResult.hasErrors()) {
            form.setPassword(null);
            return "auth/register";
        }
        try {
            authService.register(form.getUsername(), form.getPassword(), form.getEmail(), form.getFullName());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            form.setPassword(null); model.addAttribute("error", "Tên đăng nhập hoặc email vừa được sử dụng. Hãy kiểm tra lại.");
            return "auth/register";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            form.setPassword(null);
            model.addAttribute("error", ex.getMessage());
            return "auth/register";
        }
        model.addAttribute("username", form.getUsername());
        return "auth/verify-otp";
    }

    @PostMapping("/xac-thuc-otp")
    public String verifyOtp(@RequestParam String username, @RequestParam String otp, Model model) {
        boolean valid = authService.verifyOtp(username, otp);
        if (!valid) {
            model.addAttribute("username", username);
            model.addAttribute("error", "Mã OTP không đúng hoặc đã hết hạn.");
            return "auth/verify-otp";
        }
        model.addAttribute("activated", true);
        return "auth/login";
    }

    @GetMapping("/xac-thuc-otp")
    public String activationPage(Model model) { model.addAttribute("username",""); return "auth/verify-otp"; }

    @PostMapping("/xac-thuc-otp/gui-lai")
    public String resendActivation(@RequestParam String username,Model model) {
        username=PublicAccountValidation.text(username); model.addAttribute("username",username);
        if(username==null || !username.matches(PublicAccountValidation.USERNAME)) {
            model.addAttribute("error","Kiểm tra tên đăng nhập từ 3–50 ký tự."); return "auth/verify-otp";
        }
        try { authService.resendRegistrationOtp(username); } catch(IllegalStateException ex) { /* Phản hồi trung tính, không dò tài khoản. */ }
        model.addAttribute("notice","Nếu tài khoản đang chờ kích hoạt, mã mới được gửi tới email đã đăng ký. Chờ 60 giây giữa các lần gửi, tối đa 5 email/giờ.");
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
    public String sendResetOtp(@RequestParam String email, Model model) {
        email=PublicAccountValidation.email(email);
        try {
            PublicAccountValidation.requireEmail(email);
            authService.sendPasswordResetOtp(email);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error",ex.getMessage()); return "auth/forgot-password";
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
                                BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            form.clearSecrets();
            return "auth/reset-password";
        }
        if (!form.isPasswordConfirmed()) {
            form.clearSecrets();
            model.addAttribute("error", "Hai ô mật khẩu không khớp nhau.");
            return "auth/reset-password";
        }

        boolean done;
        try { done = authService.resetPassword(form.getEmail(), form.getOtp(), form.getNewPassword()); }
        catch(IllegalArgumentException | IllegalStateException ex) {
            form.clearSecrets(); model.addAttribute("error",ex.getMessage()); return "auth/reset-password";
        }
        form.clearSecrets();
        if (!done) {
            model.addAttribute("error", "Mã OTP không đúng hoặc đã hết hạn. Vui lòng gửi lại mã mới.");
            return "auth/reset-password";
        }

        model.addAttribute("passwordReset", true);
        return "auth/login";
    }


    /** Du lieu form dang ky lay tu giao dien - khong dung truc tiep Entity AppUser de nhan du lieu. */
    public static class RegisterForm {

        @NotBlank(message = "Vui lòng nhập tên đăng nhập")
        @Pattern(regexp="("+PublicAccountValidation.USERNAME+")?",message="Tên đăng nhập từ 3–50 ký tự, chỉ gồm chữ không dấu, số, dấu chấm/gạch ngang/gạch dưới.")
        private String username;

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @ValidPublicPassword
        private String password;

        @NotBlank(message = "Vui lòng nhập email")
        @vn.edu.hcmute.uteexpress.validation.ValidPublicEmail
        private String email;

        @Size(max=100,message="Họ tên tối đa 100 ký tự.")
        @Pattern(regexp="[^\\p{Cntrl}]*",message="Họ tên không chứa ký tự điều khiển.")
        private String fullName;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = PublicAccountValidation.text(username);
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
            this.email = PublicAccountValidation.email(email);
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = PublicAccountValidation.text(fullName);
        }
    }
}
