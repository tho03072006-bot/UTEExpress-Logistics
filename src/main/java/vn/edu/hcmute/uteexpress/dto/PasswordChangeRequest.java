package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu của form đổi mật khẩu khi người dùng ĐANG đăng nhập - TV1 phụ trách.
 *
 * Khác với PasswordResetRequest (quên mật khẩu): ở đây không cần OTP gửi qua email,
 * vì người dùng đã đăng nhập được nghĩa là đã cầm mật khẩu cũ. Thay vào đó bắt buộc
 * nhập lại mật khẩu hiện tại - để người nào đó mượn máy lúc chủ máy quên đăng xuất
 * cũng không đổi được mật khẩu mà chiếm tài khoản.
 */
public class PasswordChangeRequest {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 6, message = "Mật khẩu mới phải có ít nhất 6 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
    private String confirmPassword;

    public PasswordChangeRequest() {
    }

    /**
     * Hai ô mật khẩu mới có khớp nhau không. Kiểm tra bằng method thay vì viết
     * annotation kiểm tra chéo 2 field - làm giống PasswordResetRequest cho đồng bộ.
     */
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    // ----- Getter / Setter -----

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
