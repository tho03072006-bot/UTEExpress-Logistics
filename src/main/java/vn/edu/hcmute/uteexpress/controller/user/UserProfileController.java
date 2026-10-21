package vn.edu.hcmute.uteexpress.controller.user;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.controller.profile.AvatarResponses;
import vn.edu.hcmute.uteexpress.controller.profile.DisplayNameAdvice;
import vn.edu.hcmute.uteexpress.dto.profile.ProfileDetailForm;
import vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException;
import vn.edu.hcmute.uteexpress.service.profile.AccountProfileService;
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
    private final AccountProfileService accountProfiles;

    public UserProfileController(UserProfileService userProfileService,
                                 EmailChangeService emailChangeService,
                                 AccountProfileService accountProfiles) {
        this.userProfileService = userProfileService;
        this.emailChangeService = emailChangeService;
        this.accountProfiles = accountProfiles;
    }

    // ----- Ảnh đại diện, thông tin cá nhân, tùy chọn email -----

    @GetMapping("/anh-dai-dien")
    public ResponseEntity<byte[]> avatar(Authentication authentication,
                                         @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return AvatarResponses.own(accountProfiles, authentication.getName(), ifNoneMatch);
    }

    @PostMapping("/anh-dai-dien")
    public String uploadAvatar(@RequestParam("avatar") MultipartFile avatar, Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            accountProfiles.updateAvatar(authentication.getName(), avatar);
            redirectAttributes.addFlashAttribute("message", "Đã cập nhật ảnh đại diện.");
        } catch (IllegalArgumentException | CloudStorageUnavailableException ex) {
            redirectAttributes.addFlashAttribute("error", ex instanceof IllegalArgumentException
                    ? ex.getMessage() : "Chưa lưu được ảnh lúc này, vui lòng thử lại sau.");
        }
        return "redirect:/nguoi-dung/tai-khoan";
    }

    @PostMapping("/anh-dai-dien/xoa")
    public String removeAvatar(Authentication authentication, RedirectAttributes redirectAttributes) {
        accountProfiles.removeAvatar(authentication.getName());
        redirectAttributes.addFlashAttribute("message", "Đã xóa ảnh đại diện.");
        return "redirect:/nguoi-dung/tai-khoan";
    }

    @PostMapping("/ca-nhan")
    public String updateDetails(@Valid @ModelAttribute("detailForm") ProfileDetailForm detailForm,
                                BindingResult bindingResult, Authentication authentication,
                                Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                accountProfiles.updateDetails(authentication.getName(), detailForm);
                redirectAttributes.addFlashAttribute("message", "Đã lưu thông tin cá nhân.");
                return "redirect:/nguoi-dung/tai-khoan";
            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        fillProfileModel(authentication, model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form",
                    ProfileUpdateRequest.from(userProfileService.findByUsername(authentication.getName())));
        }
        return "user/profile";
    }

    @PostMapping("/thong-bao")
    public String updateNotifications(@RequestParam(defaultValue = "false") boolean notifyOrderEmail,
                                      Authentication authentication, RedirectAttributes redirectAttributes) {
        accountProfiles.updateNotifications(authentication.getName(), notifyOrderEmail);
        redirectAttributes.addFlashAttribute("message", notifyOrderEmail
                ? "Đã bật email cập nhật vận đơn." : "Đã tắt email cập nhật vận đơn.");
        return "redirect:/nguoi-dung/tai-khoan";
    }

    @GetMapping
    public String viewProfile(Authentication authentication, Model model, HttpSession session) {
        fillProfileModel(authentication, model);
        AvatarResponses.remember(session, (AccountProfileService.View) model.getAttribute("profile"));
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
                                Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        if (bindingResult.hasErrors()) {
            fillProfileModel(authentication, model);
            return "user/profile";
        }
        session.removeAttribute(DisplayNameAdvice.SESSION_KEY);

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
        AccountProfileService.View profile = accountProfiles.view(authentication.getName());
        model.addAttribute("profile", profile);
        model.addAttribute("provinces", ProfileDetailForm.PROVINCES);
        model.addAttribute("genders", vn.edu.hcmute.uteexpress.entity.UserProfileDetail.Gender.values());
        if (!model.containsAttribute("detailForm")) {
            model.addAttribute("detailForm", ProfileDetailForm.from(profile.detail()));
        }
    }
}
