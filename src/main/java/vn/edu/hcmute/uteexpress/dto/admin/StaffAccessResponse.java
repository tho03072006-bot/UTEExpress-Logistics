package vn.edu.hcmute.uteexpress.dto.admin;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;

public record StaffAccessResponse(Long id, String username, AppUser.Role role, boolean active,
        boolean passwordChangeRequired, long accessVersion, long revision) {
    public static StaffAccessResponse from(StaffAccount account) {
        return new StaffAccessResponse(account.getId(), account.getUser().getUsername(), account.getUser().getRole(),
            account.isActive() && account.getUser().isEnabled() && !account.getUser().isLockedByAdmin(),
            account.isPasswordChangeRequired(), account.getAccessVersion(), account.getRevision());
    }
}
