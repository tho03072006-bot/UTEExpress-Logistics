package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import vn.edu.hcmute.uteexpress.util.AuthInputRules;

/**
 * Dữ liệu người dùng nhập ở bước đặt lại mật khẩu (sau khi đã nhận mã OTP qua email).
 *
 * Email được mang theo trong ô ẩn của form chứ không đặt trên URL - tránh để địa chỉ email
 * của người dùng nằm trong lịch sử trình duyệt hay log của máy chủ.
 */
public class PasswordResetRequest {

    @NotBlank(message = "Vui lòng nhập email đã đăng ký")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP gồm đúng 6 chữ số")
    private String otp;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 6, max = 72, message = "Mật khẩu mới cần từ 6 đến 72 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
    @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
    private String confirmPassword;

    public PasswordResetRequest() {
    }

    /**
     * Hai ô mật khẩu có khớp nhau không. Kiểm tra ở Controller thay vì viết annotation
     * kiểm tra chéo 2 field cho gọn - chỉ dùng đúng một chỗ.
     */
    @AssertTrue(message = "Hai ô mật khẩu mới chưa khớp nhau")
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8")
    public boolean isPasswordWithinByteLimit() {
        return AuthInputRules.passwordWithinByteLimit(newPassword)
                && AuthInputRules.passwordWithinByteLimit(confirmPassword);
    }

    // ----- Getter / Setter -----

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = AuthInputRules.normalizeEmail(email);
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = AuthInputRules.trim(otp);
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
