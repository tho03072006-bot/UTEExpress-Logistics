package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void dangKy(String username, String rawPassword, String email) {
        // TODO: kiem tra trung username/email, tao AppUser voi password da ma hoa
        // (passwordEncoder.encode(rawPassword)), sinh ma OTP 6 so, luu otpCode + otpExpiry,
        // roi goi JavaMailSender de gui OTP qua email (Spring Mail, xem application.properties).
        throw new UnsupportedOperationException("TODO: hien thuc dang ky + gui OTP email");
    }

    @Override
    public boolean xacThucOtp(String username, String otpCode) {
        // TODO: so sanh otpCode + kiem tra otpExpiry, neu dung thi set enabled = true
        throw new UnsupportedOperationException("TODO: hien thuc xac thuc OTP");
    }

    @Override
    public void guiOtpQuenMatKhau(String email) {
        // TODO: tim user theo email, sinh OTP moi, gui email
        throw new UnsupportedOperationException("TODO: hien thuc gui OTP quen mat khau");
    }

    @Override
    public void datLaiMatKhau(String email, String otpCode, String matKhauMoi) {
        // TODO: kiem tra OTP hop le roi cap nhat password (nho ma hoa bang passwordEncoder)
        throw new UnsupportedOperationException("TODO: hien thuc dat lai mat khau");
    }
}
