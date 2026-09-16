package vn.edu.hcmute.uteexpress.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.service.AuthService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 10;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    public AuthServiceImpl(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                            JavaMailSender mailSender) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    @Override
    @Transactional
    public void register(String username, String rawPassword, String email, String fullName) {
        if (appUserRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại");
        }
        if (appUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được đăng ký");
        }

        AppUser appUser = new AppUser(username, passwordEncoder.encode(rawPassword), email);
        appUser.setFullName(fullName);
        appUser.setRole(AppUser.Role.USER);
        appUser.setEnabled(false); // chi bat len sau khi xac thuc OTP

        String otp = generateOtp();
        appUser.setOtpCode(otp);
        appUser.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));

        appUserRepository.save(appUser);
        sendOtpEmail(email, otp, "Ma xac thuc dang ky", "kich hoat tai khoan");
    }

    @Override
    public boolean verifyOtp(String username, String otpCode) {
        Optional<AppUser> found = appUserRepository.findByUsername(username);
        if (found.isEmpty() || found.get().isEnabled()) {
            return false;
        }
        AppUser appUser = found.get();

        if (!isOtpValid(appUser, otpCode)) {
            return false;
        }

        appUser.setEnabled(true);
        clearOtp(appUser);
        appUserRepository.save(appUser);
        return true;
    }

    @Override
    @Transactional
    public void sendPasswordResetOtp(String email) {
        Optional<AppUser> found = appUserRepository.findByEmail(email);

        if (found.isEmpty()) {
            // Khong bao loi ra man hinh - xem giai thich o AuthService. Chi ghi log de nguoi
            // phat trien biet co ai do go nham email, khong lo thong tin ra nguoi dung.
            log.info("Co yeu cau dat lai mat khau cho email chua dang ky, bo qua.");
            return;
        }

        AppUser appUser = found.get();
        if (!appUser.isEnabled()) {
            // Tai khoan chua kich hoat thi phai di duong dang ky/OTP kich hoat, khong phai duong nay.
            // Neu van gui OTP o day, ma do co the bi dung nguoc lai de kich hoat tai khoan chua xac thuc.
            log.info("Co yeu cau dat lai mat khau cho tai khoan chua kich hoat, bo qua.");
            return;
        }

        String otp = generateOtp();
        appUser.setOtpCode(otp);
        appUser.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        appUserRepository.save(appUser);

        sendOtpEmail(email, otp, "Ma dat lai mat khau", "dat lai mat khau");
    }

    @Override
    public boolean resetPassword(String email, String otpCode, String newRawPassword) {
        Optional<AppUser> found = appUserRepository.findByEmail(email);
        if (found.isEmpty()) {
            return false;
        }

        AppUser appUser = found.get();
        if (!appUser.isEnabled() || !isOtpValid(appUser, otpCode)) {
            return false;
        }

        appUser.setPassword(passwordEncoder.encode(newRawPassword));
        // Xoa OTP ngay sau khi dung - moi ma chi doi mat khau duoc dung mot lan.
        clearOtp(appUser);
        appUserRepository.save(appUser);
        return true;
    }

    // ----- Phần dùng chung trong service -----

    /** Mã đúng và còn trong hạn 10 phút thì mới hợp lệ. */
    private boolean isOtpValid(AppUser appUser, String otpCode) {
        return otpCode != null
                && appUser.getOtpCode() != null
                && otpCode.equals(appUser.getOtpCode())
                && appUser.getOtpExpiry() != null
                && appUser.getOtpExpiry().isAfter(LocalDateTime.now());
    }

    private void clearOtp(AppUser appUser) {
        appUser.setOtpCode(null);
        appUser.setOtpExpiry(null);
    }

    /** Không ghi mã OTP vào log; lỗi gửi mail làm rollback mã vừa lưu. */
    private void sendOtpEmail(String toEmail, String otp, String subject, String purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("[UTEExpress] " + subject);
            message.setText("Ma OTP de " + purpose + " cua ban la: " + otp
                    + " (het han sau " + OTP_EXPIRY_MINUTES + " phut)."
                    + " Neu khong phai ban yeu cau, hay bo qua email nay.");
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Khong gui duoc email OTP de {}. Kiem tra cau hinh SMTP: {}",
                    purpose, ex.getClass().getSimpleName());
            throw new IllegalStateException("Không gửi được email OTP. Vui lòng thử lại sau.");
        }
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
