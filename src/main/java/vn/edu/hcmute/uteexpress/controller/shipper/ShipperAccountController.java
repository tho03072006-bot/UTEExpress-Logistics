package vn.edu.hcmute.uteexpress.controller.shipper;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.controller.profile.AvatarResponses;
import vn.edu.hcmute.uteexpress.dto.PasswordChangeRequest;
import vn.edu.hcmute.uteexpress.dto.ProfileUpdateRequest;
import vn.edu.hcmute.uteexpress.dto.profile.ProfileDetailForm;
import vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException;
import vn.edu.hcmute.uteexpress.service.UserProfileService;
import vn.edu.hcmute.uteexpress.service.profile.AccountProfileService;

/**
 * Trang "Tài khoản của tôi" cho Shipper: ảnh đại diện, họ tên/số điện thoại, thông tin cá nhân,
 * thống kê giao hàng và đổi mật khẩu. Email Shipper do quản trị cấp nên chỉ xem, không tự đổi ở đây.
 */
@Controller
@RequestMapping("/shipper/tai-khoan")
public class ShipperAccountController {
    private static final String BASE = "/shipper/tai-khoan";

    private final AccountProfileService accountProfiles;
    private final UserProfileService userProfileService;

    public ShipperAccountController(AccountProfileService accountProfiles, UserProfileService userProfileService) {
        this.accountProfiles = accountProfiles;
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public String view(Authentication authentication, Model model, HttpSession session) {
        fill(authentication, model);
        AvatarResponses.remember(session, (AccountProfileService.View) model.getAttribute("profile"));
        return "shipper/account";
    }

    @PostMapping("/cap-nhat")
    public String updateContact(@Valid @ModelAttribute("form") ProfileUpdateRequest form, BindingResult bindingResult,
                                Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasFieldErrors("fullName") || bindingResult.hasFieldErrors("phone")) {
            fill(authentication, model);
            return "shipper/account";
        }
        // Email Shipper do quản trị cấp: luôn giữ email đang lưu, bỏ qua giá trị gửi lên.
        form.setEmail(userProfileService.findByUsername(authentication.getName()).getEmail());
        try {
            userProfileService.updateProfile(authentication.getName(), form);
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + BASE;
        }
        redirectAttributes.addFlashAttribute("message", "Đã cập nhật họ tên và số điện thoại.");
        return "redirect:" + BASE;
    }

    @PostMapping("/ca-nhan")
    public String updateDetails(@Valid @ModelAttribute("detailForm") ProfileDetailForm detailForm,
                                BindingResult bindingResult, Authentication authentication,
                                Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                accountProfiles.updateDetails(authentication.getName(), detailForm);
                redirectAttributes.addFlashAttribute("message", "Đã lưu thông tin cá nhân.");
                return "redirect:" + BASE;
            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        fill(authentication, model);
        return "shipper/account";
    }

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
        return "redirect:" + BASE;
    }

    @PostMapping("/anh-dai-dien/xoa")
    public String removeAvatar(Authentication authentication, RedirectAttributes redirectAttributes) {
        accountProfiles.removeAvatar(authentication.getName());
        redirectAttributes.addFlashAttribute("message", "Đã xóa ảnh đại diện.");
        return "redirect:" + BASE;
    }

    @GetMapping("/doi-mat-khau")
    public String changePasswordForm(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new PasswordChangeRequest());
        model.addAttribute("accountBase", BASE);
        return "user/change-password";
    }

    @PostMapping("/doi-mat-khau")
    public String changePassword(@Valid @ModelAttribute("form") PasswordChangeRequest form, BindingResult bindingResult,
                                 Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("accountBase", BASE);
        if (bindingResult.hasErrors()) return "user/change-password";
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
        redirectAttributes.addFlashAttribute("message", "Đã đổi mật khẩu. Lần đăng nhập sau hãy dùng mật khẩu mới.");
        return "redirect:" + BASE;
    }

    private void fill(Authentication authentication, Model model) {
        AccountProfileService.View profile = accountProfiles.view(authentication.getName());
        model.addAttribute("profile", profile);
        model.addAttribute("provinces", ProfileDetailForm.PROVINCES);
        model.addAttribute("genders", vn.edu.hcmute.uteexpress.entity.UserProfileDetail.Gender.values());
        if (!model.containsAttribute("form")) model.addAttribute("form", ProfileUpdateRequest.from(profile.account()));
        if (!model.containsAttribute("detailForm")) model.addAttribute("detailForm", ProfileDetailForm.from(profile.detail()));
    }
}
