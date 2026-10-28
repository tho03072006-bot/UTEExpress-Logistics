package vn.edu.hcmute.uteexpress.dto.admin;

import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;

/** Chỉ trả dữ liệu của chính nhân viên; không có mật khẩu, OTP, JWT hoặc phiên bản truy cập. */
public record StaffProfileResponse(String username, String fullName, String email, String phone,
        AppUser.Role role, LocalDateTime issuedAt) {
    public static StaffProfileResponse from(StaffAccount account) {
        var user = account.getUser();
        return new StaffProfileResponse(user.getUsername(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRole(), account.getCreatedAt());
    }
}
