package vn.edu.hcmute.uteexpress.controller.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            return "auth/register";
        }
        try {
            authService.register(form.getUsername(), form.getPassword(), form.getEmail(), form.getFullName());
        } catch (IllegalArgumentException ex) {
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
            model.addAttribute("error", "Ma OTP khong dung hoac da het han.");
            return "auth/verify-otp";
        }
        model.addAttribute("activated", true);
        return "auth/login";
    }

    // TODO: @GetMapping("/quen-mat-khau"), @PostMapping("/quen-mat-khau") - lam sau, cung mau voi tren

    /** Du lieu form dang ky lay tu giao dien - khong dung truc tiep Entity AppUser de nhan du lieu. */
    public static class RegisterForm {

        @NotBlank(message = "Vui long nhap ten dang nhap")
        private String username;

        @NotBlank(message = "Vui long nhap mat khau")
        private String password;

        @NotBlank(message = "Vui long nhap email")
        private String email;

        private String fullName;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
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
            this.email = email;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }
    }
}
