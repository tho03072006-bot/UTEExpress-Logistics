package vn.edu.hcmute.uteexpress.controller.user;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.EmailOtpRequest;
import vn.edu.hcmute.uteexpress.dto.PasswordChangeRequest;
import vn.edu.hcmute.uteexpress.dto.ProfileUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.EmailChangeRequest;
import vn.edu.hcmute.uteexpress.service.EmailChangeService;
import vn.edu.hcmute.uteexpress.service.UserProfileService;

import java.util.Optional;

/**
 * Hồ sơ tài khoản của người gửi hàng - TV1 phụ trách.
 * Controller chỉ nhận dữ liệu, gọi Service và điều hướng; mọi kiểm tra nghiệp vụ
 * (trùng email, đúng mật khẩu cũ, mã OTP còn hạn) đều nằm trong tầng Service.
 */
@Controller
@RequestMapping("/nguoi-dung/tai-khoan")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final EmailChangeService emailChangeService;

    public UserProfileController(UserProfileService userProfileService,
                                 EmailChangeService emailChangeService) {
        this.userProfileService = userProfileService;
        this.emailChangeService = emailChangeService;
    }

    @GetMapping
    public String viewProfile(Authentication authentication, Model model) {
        fillProfileModel(authentication, model);
        // Chỉ nạp form khi chưa có sẵn: nếu vừa submit lỗi và quay lại đây thì phải giữ
        // nguyên những gì người dùng đã gõ, không ghi đè bằng dữ liệu cũ trong DB.
        if (!model.containsAttribute("form")) {
            model.addAttribute("form",
                    ProfileUpdateRequest.from(userProfileService.findByUsername(authentication.getName())));
        }
        return "user/profile";
    }

    @PostMapping("/cap-nhat")
    public String updateProfile(@Valid @ModelAttribute("form") ProfileUpdateRequest form,
                                BindingResult bindingResult, Authentication authentication,
                                Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            fillProfileModel(authentication, model);
            return "user/profile";
        }

        boolean needEmailVerification;
        try {
            needEmailVerification = userProfileService.updateProfile(authentication.getName(), form);
        } catch (IllegalStateException ex) {
            fillProfileModel(authentication, model);
            model.addAttribute("error", ex.getMessage());
            return "user/profile";
        }

        if (needEmailVerification) {
            redirectAttributes.addFlashAttribute("message",
                    "Đã lưu họ tên và số điện thoại. Còn email thì cần xác thực thêm một bước nữa.");
            return "redirect:/nguoi-dung/tai-khoan/xac-thuc-email";
        }
        redirectAttributes.addFlashAttribute("message", "Đã cập nhật thông tin tài khoản.");
        return "redirect:/nguoi-dung/tai-khoan";
    }

    // ----- Xác thực OTP khi đổi email -----

    @GetMapping("/xac-thuc-email")
    public String verifyEmailForm(Authentication authentication, Model model,
                                  RedirectAttributes redirectAttributes) {
        Optional<EmailChangeRequest> pending = emailChangeService.findPending(authentication.getName());
        if (pending.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không có yêu cầu đổi email nào đang chờ xác thực.");
            return "redirect:/nguoi-dung/tai-khoan";
        }
        model.addAttribute("pendingEmail", pending.get().getNewEmail());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new EmailOtpRequest());
        }
        return "user/verify-email";
    }

    @PostMapping("/xac-thuc-email")
    public String verifyEmail(@Valid @ModelAttribute("form") EmailOtpRequest form,
                              BindingResult bindingResult, Authentication authentication,
                              Model model, RedirectAttributes redirectAttributes) {
        Optional<EmailChangeRequest> pending = emailChangeService.findPending(authentication.getName());
        if (pending.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Không có yêu cầu đổi email nào đang chờ xác thực.");
            return "redirect:/nguoi-dung/tai-khoan";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("pendingEmail", pending.get().getNewEmail());
            return "user/verify-email";
        }

        try {
            emailChangeService.confirm(authentication.getName(), form.getOtp());
        } catch (IllegalStateException ex) {
            // Nhập sai quá số lần hoặc mã hết hạn thì Service đã xoá yêu cầu - lúc đó
            // không còn gì để nhập nữa nên đưa người dùng về thẳng trang tài khoản.
            if (emailChangeService.findPending(authentication.getName()).isEmpty()) {
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
                return "redirect:/nguoi-dung/tai-khoan";
            }
            model.addAttribute("pendingEmail", pending.get().getNewEmail());
            model.addAttribute("error", ex.getMessage());
            return "user/verify-email";
        }

        redirectAttributes.addFlashAttribute("message", "Đã đổi email thành công.");
        return "redirect:/nguoi-dung/tai-khoan";
    }

    @PostMapping("/xac-thuc-email/gui-lai")
    public String resendEmailOtp(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            emailChangeService.resendOtp(authentication.getName());
            redirectAttributes.addFlashAttribute("message", "Đã gửi lại mã xác thực mới. Mã cũ không còn dùng được.");
            return "redirect:/nguoi-dung/tai-khoan/xac-thuc-email";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/nguoi-dung/tai-khoan";
        }
    }

    @PostMapping("/xac-thuc-email/huy")
    public String cancelEmailChange(Authentication authentication, RedirectAttributes redirectAttributes) {
        emailChangeService.cancel(authentication.getName());
        redirectAttributes.addFlashAttribute("message", "Đã huỷ yêu cầu đổi email. Email cũ được giữ nguyên.");
        return "redirect:/nguoi-dung/tai-khoan";
    }

    // ----- Đổi mật khẩu -----

    @GetMapping("/doi-mat-khau")
    public String changePasswordForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PasswordChangeRequest());
        }
        return "user/change-password";
    }

    @PostMapping("/doi-mat-khau")
    public String changePassword(@Valid @ModelAttribute("form") PasswordChangeRequest form,
                                 BindingResult bindingResult, Authentication authentication,
                                 Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "user/change-password";
        }
        if (!form.isPasswordConfirmed()) {
            model.addAttribute("error", "Hai ô mật khẩu mới chưa khớp nhau.");
            return "user/change-password";
        }
        try {
            userProfileService.changePassword(authentication.getName(), form);
        } catch (IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            return "user/change-password";
        }
        redirectAttributes.addFlashAttribute("message",
                "Đã đổi mật khẩu thành công. Lần đăng nhập sau hãy dùng mật khẩu mới.");
        return "redirect:/nguoi-dung/tai-khoan";
    }

    // ----- Phần dùng chung trong controller -----

    /**
     * Trang hồ sơ luôn cần tài khoản hiện tại, và cần biết có yêu cầu đổi email nào
     * đang treo hay không để hiện dải nhắc phía trên.
     */
    private void fillProfileModel(Authentication authentication, Model model) {
        AppUser appUser = userProfileService.findByUsername(authentication.getName());
        model.addAttribute("account", appUser);
        model.addAttribute("pendingEmail", emailChangeService.findPending(authentication.getName())
                .map(EmailChangeRequest::getNewEmail)
                .orElse(null));
    }
}
