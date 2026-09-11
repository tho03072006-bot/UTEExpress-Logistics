package vn.edu.hcmute.uteexpress.service;

/**
 * Chuc nang chung: dang ky + OTP email, dang nhap, quen mat khau + OTP email.
 * TODO: hien thuc trong AuthServiceImpl - day la nen tang ca nhom dung chung,
 * nen uu tien lam xong o Tuan 2 theo ke hoach truoc khi tach nhanh feature rieng.
 */
public interface AuthService {

    void dangKy(String username, String rawPassword, String email);

    boolean xacThucOtp(String username, String otpCode);

    void guiOtpQuenMatKhau(String email);

    void datLaiMatKhau(String email, String otpCode, String matKhauMoi);
}
