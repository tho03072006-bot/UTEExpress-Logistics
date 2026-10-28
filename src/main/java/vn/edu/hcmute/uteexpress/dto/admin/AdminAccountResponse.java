package vn.edu.hcmute.uteexpress.dto.admin;

import vn.edu.hcmute.uteexpress.entity.AppUser;

/** Không đưa mật khẩu, OTP hoặc entity vào trang quản trị. */
public record AdminAccountResponse(Long id, String username, String fullName, String email,
        String phone, AppUser.Role role, boolean active, boolean lockedByAdmin, long revision) {
    public static AdminAccountResponse from(AppUser user) {
        return new AdminAccountResponse(user.getId(), user.getUsername(), user.getFullName(),
            user.getEmail(), user.getPhone(), user.getRole(), user.isEnabled() && !user.isLockedByAdmin(),
            user.isLockedByAdmin(), user.getRevision());
    }
    public boolean isInternal() { return role == AppUser.Role.ADMIN || role == AppUser.Role.MANAGER; }
}
