package vn.edu.hcmute.uteexpress.dto.admin;

import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;

public record StaffAccountResponse(Long id, String username, String fullName, String email,
        String role, boolean active, boolean passwordChangeRequired, long revision, String issuedBy) {
    public static StaffAccountResponse from(StaffAccount account) {
        var user = account.getUser();
        return new StaffAccountResponse(account.getId(), user.getUsername(), user.getFullName(),
                user.getEmail(), user.getRole().name(), account.isActive() && user.isEnabled(),
                account.isPasswordChangeRequired(), account.getRevision(), account.getIssuedBy());
    }
}

