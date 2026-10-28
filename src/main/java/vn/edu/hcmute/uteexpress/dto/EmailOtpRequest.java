package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Mã OTP người dùng nhập ở bước xác thực đổi email - TV1 phụ trách.
 *
 * Chỉ có đúng một ô mã. Địa chỉ email mới KHÔNG nằm trong form mà được đọc lại từ bảng
 * email_change_request theo tài khoản đang đăng nhập - để dù có sửa dữ liệu gửi lên
 * cũng không đổi được sang một địa chỉ khác với địa chỉ đã thực sự nhận mã.
 */
public class EmailOtpRequest {

    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP gồm đúng 6 chữ số")
    private String otp;

    public EmailOtpRequest() {
    }

    // ----- Getter / Setter -----

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
