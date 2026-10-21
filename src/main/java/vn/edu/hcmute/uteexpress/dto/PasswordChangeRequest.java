package vn.edu.hcmute.uteexpress.dto;

import vn.edu.hcmute.uteexpress.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Pattern;
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
    @Size(max = 100, message = "Mật khẩu hiện tại không đúng")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 6, max = 64, message = "Mật khẩu mới từ 6 đến 64 ký tự")
    @Pattern(regexp = "[^\\p{Cntrl}]*", message = "Mật khẩu không được chứa ký tự điều khiển")
    @MaxUtf8Bytes
    private String newPassword;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
    @Size(max = 64, message = "Mật khẩu nhập lại không khớp")
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
