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
import vn.edu.hcmute.uteexpress.util.MailFromNameSetter;
import vn.edu.hcmute.uteexpress.util.AuthInputRules;
import vn.edu.hcmute.uteexpress.util.OtpGuard;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private final OtpGuard otpGuard = new OtpGuard();

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final MailFromNameSetter mailFromNameSetter;

    public AuthServiceImpl(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                            JavaMailSender mailSender, MailFromNameSetter mailFromNameSetter) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailFromNameSetter = mailFromNameSetter;
    }

    @Override
    @Transactional
    public void register(String username, String rawPassword, String email, String fullName) {
        username = AuthInputRules.username(username);
        email = AuthInputRules.email(email);
        fullName = AuthInputRules.text(fullName, 100, "họ tên", false);
        AuthInputRules.password(rawPassword, true);
        if (appUserRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại");
        }
        if (appUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được đăng ký");
        }
        requireSendAllowed(email, null);

        AppUser appUser = new AppUser(username, passwordEncoder.encode(rawPassword), email);
        appUser.setFullName(fullName);
        appUser.setRole(AppUser.Role.USER);
        appUser.setEnabled(false); // chi bat len sau khi xac thuc OTP

        String otp = generateOtp();
        appUser.setOtpCode(otp);
        appUser.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));

        appUserRepository.save(appUser);
        sendOtpEmail(email, otp, "Mã xác thực đăng ký", "kích hoạt tài khoản");
    }

    @Override
    @Transactional
    public boolean verifyOtp(String username, String otpCode) {
        if (username == null || username.length() > 50) return false;
        Optional<AppUser> found = appUserRepository.findByUsernameForUpdate(username.strip());
        if (found.isEmpty() || found.get().isEnabled()) {
            return false;
        }
        AppUser appUser = found.get();

        if (!consumeAttempt(appUser, otpCode, "activation")) {
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
        email = AuthInputRules.email(email);
        Optional<AppUser> found = appUserRepository.findByEmailForUpdate(email);

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
        requireSendAllowed(appUser.getEmail(), appUser);

        String otp = generateOtp();
        appUser.setOtpCode(otp);
        appUser.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        appUserRepository.save(appUser);

        sendOtpEmail(email, otp, "Mã đặt lại mật khẩu", "đặt lại mật khẩu");
    }

    @Override
    @Transactional
    public void resendActivationOtp(String username) {
        if (username == null || username.length() > 50) return;
        Optional<AppUser> found = appUserRepository.findByUsernameForUpdate(username.strip());
        if (found.isEmpty() || found.get().isEnabled()) return;
        AppUser appUser = found.get();
        requireSendAllowed(appUser.getEmail(), appUser);
        String otp = generateOtp();
        appUser.setOtpCode(otp);
        appUser.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        appUserRepository.save(appUser);
        sendOtpEmail(appUser.getEmail(), otp, "Mã xác thực đăng ký", "kích hoạt tài khoản");
    }

    @Override
    @Transactional
    public boolean resetPassword(String email, String otpCode, String newRawPassword) {
        AuthInputRules.password(newRawPassword, true);
        email = AuthInputRules.email(email);
        Optional<AppUser> found = appUserRepository.findByEmailForUpdate(email);
        if (found.isEmpty()) {
            return false;
        }

        AppUser appUser = found.get();
        if (!appUser.isEnabled() || !consumeAttempt(appUser, otpCode, "reset")) {
            return false;
        }

        appUser.setPassword(passwordEncoder.encode(newRawPassword));
        // Xoa OTP ngay sau khi dung - moi ma chi doi mat khau duoc dung mot lan.
        clearOtp(appUser);
        appUserRepository.save(appUser);
        return true;
    }

    // ----- Phần dùng chung trong service -----

    /** Lock is held until commit; failures return normally so cleanup is committed. */
    private boolean consumeAttempt(AppUser appUser, String otpCode, String purpose) {
        if (appUser.getOtpCode() == null || appUser.getOtpExpiry() == null) return false;
        LocalDateTime now = LocalDateTime.now();
        if (!appUser.getOtpExpiry().isAfter(now)) {
            clearOtp(appUser);
            appUserRepository.save(appUser);
            return false;
        }
        String key = purpose + ":" + appUser.getUsername().toLowerCase(Locale.ROOT)
                + ":" + appUser.getOtpCode() + ":" + appUser.getOtpExpiry();
        int remaining = otpGuard.take(key, MAX_OTP_ATTEMPTS, Duration.between(now, appUser.getOtpExpiry()));
        boolean valid = remaining >= 0 && otpCode != null
                && otpCode.strip().matches("[0-9]{6}") && otpCode.strip().equals(appUser.getOtpCode());
        if (!valid && remaining <= 0) {
            clearOtp(appUser);
            appUserRepository.save(appUser);
        }
        return valid;
    }

    private void requireSendAllowed(String email, AppUser appUser) {
        LocalDateTime now = LocalDateTime.now();
        // Expiry records the issue time without adding a database column.
        if (appUser != null && appUser.getOtpExpiry() != null
                && appUser.getOtpExpiry().minusMinutes(OTP_EXPIRY_MINUTES).plusSeconds(60).isAfter(now)) {
            throw new IllegalStateException("Vui lòng chờ 60 giây trước khi yêu cầu mã OTP mới.");
        }
        if (otpGuard.take("send:" + email.toLowerCase(Locale.ROOT), 1, SEND_COOLDOWN) < 0) {
            throw new IllegalStateException("Vui lòng chờ 60 giây trước khi yêu cầu mã OTP mới.");
        }
    }

    private void clearOtp(AppUser appUser) {
        appUser.setOtpCode(null);
        appUser.setOtpExpiry(null);
    }

    /** Không ghi mã OTP vào log; lỗi gửi mail làm rollback mã vừa lưu. */
    private void sendOtpEmail(String toEmail, String otp, String subject, String purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            mailFromNameSetter.applyTo(message);
            message.setTo(toEmail);
            message.setSubject("[UTEExpress] " + subject);
            message.setText("Chào bạn,\n\n"
                    + "Mã OTP để " + purpose + " của bạn là: " + otp + "\n"
                    + "Mã có hiệu lực trong " + OTP_EXPIRY_MINUTES + " phút.\n\n"
                    + "Nếu không phải bạn yêu cầu, hãy bỏ qua email này.\n\n"
                    + "Trân trọng,\n"
                    + "UTEExpress");
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
