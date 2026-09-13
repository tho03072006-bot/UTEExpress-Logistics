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

    /**
     * Gui ma OTP dat lai mat khau toi email.
     *
     * CO Y KHONG nem loi khi email khong ton tai hoac tai khoan chua kich hoat:
     * neu bao "email nay chua dang ky" thi nguoi la co the do xem email nao co tai khoan
     * trong he thong (user enumeration). Man hinh luon hien cung mot thong bao,
     * ai khong co tai khoan thi don gian la khong nhan duoc mail nao.
     */
    void sendPasswordResetOtp(String email);

    /**
     * Doi mat khau bang ma OTP da gui. Tra ve false neu email khong ton tai,
     * ma OTP sai hoac da het han. Mat khau moi luon duoc ma hoa BCrypt truoc khi luu.
     */
    boolean resetPassword(String email, String otpCode, String newRawPassword);
}
