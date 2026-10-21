package vn.edu.hcmute.uteexpress.security;

/** Chỉ lỗi nhập OTP được commit bộ đếm; lỗi SMTP/database vẫn rollback như bình thường. */
public class OtpRejectedException extends IllegalStateException {
    public OtpRejectedException(String message) { super(message); }
}
