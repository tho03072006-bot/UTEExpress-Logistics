package vn.edu.hcmute.uteexpress.service;

/**
 * Chuc nang chung: dang ky + OTP email, dang nhap (do Spring Security xu ly),
 * quen mat khau + OTP email.
 */
public interface AuthService {

    /** Tao tai khoan moi (chua kich hoat) va gui OTP xac thuc qua email. */
    void register(String username, String rawPassword, String email, String fullName);

    /** Kiem tra OTP dang ky, neu dung thi kich hoat tai khoan (enabled = true). */
    boolean verifyOtp(String username, String otpCode);

    // TODO: sendForgotPasswordOtp(String email) + resetPassword(...) - chua lam trong ban demo nay,
    // se hoan thien theo cung mau voi register()/verifyOtp() o tren.
}
