package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StaffPasswordChangeRequest extends StaffPasswordRequest {
    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
    @Size(max = 100, message = "Mật khẩu hiện tại không hợp lệ.")
    private String currentPassword;
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    @Override
    public void clearSecrets() { super.clearSecrets(); currentPassword = null; }
}

