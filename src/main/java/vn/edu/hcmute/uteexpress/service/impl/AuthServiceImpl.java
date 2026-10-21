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
import vn.edu.hcmute.uteexpress.security.OtpPolicy;
import vn.edu.hcmute.uteexpress.validation.PublicAccountValidation;

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
    private final MailFromNameSetter mailFromNameSetter;
    private final OtpPolicy otpPolicy;
    private final vn.edu.hcmute.uteexpress.repository.EmailChangeRequestRepository emailChanges;

    public AuthServiceImpl(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                            JavaMailSender mailSender, MailFromNameSetter mailFromNameSetter, OtpPolicy otpPolicy,
                            vn.edu.hcmute.uteexpress.repository.EmailChangeRequestRepository emailChanges) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailFromNameSetter = mailFromNameSetter;
        this.otpPolicy = otpPolicy;
        this.emailChanges=emailChanges;
    }

    @Override
    @Transactional
    public void register(String username, String rawPassword, String email, String fullName) {
        username = PublicAccountValidation.text(username); email = PublicAccountValidation.email(email);
        fullName = PublicAccountValidation.text(fullName);
        PublicAccountValidation.requireRegistration(username, rawPassword, email, fullName);
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại");
        }
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email đã được đăng ký");
        }

        AppUser appUser = new AppUser(username, passwordEncoder.encode(rawPassword), email);
        appUser.setFullName(fullName);
        appUser.setRole(AppUser.Role.USER);
        appUser.setEnabled(false); // chi bat len sau khi xac thuc OTP

        String otp = generateOtp();
        otpPolicy.reserveSend(appUser); otpPolicy.issue(appUser, otp);

        appUserRepository.saveAndFlush(appUser);
        sendOtpEmail(email, otp, "Mã xác thực đăng ký", "kích hoạt tài khoản");
    }

    @Override
    @Transactional
    public boolean verifyOtp(String username, String otpCode) {
        if (username == null || username.length() > 50) return false;
        Optional<AppUser> found = appUserRepository.findLockedByUsername(username.strip());
        if (found.isEmpty() || found.get().isEnabled() || !allowsPublicOtp(found.get())) {
            return false;
        }
        AppUser appUser = found.get();

        int previousAttempts=appUser.getOtpAttempts();
        if (!otpPolicy.consume(appUser, otpCode)) {
            if(previousAttempts!=appUser.getOtpAttempts()) appUserRepository.save(appUser);
            return false;
        }

        appUser.setEnabled(true);
        appUserRepository.save(appUser);
        return true;
    }

    @Override
    @Transactional
    public void sendPasswordResetOtp(String email) {
        email = PublicAccountValidation.email(email); PublicAccountValidation.requireEmail(email);
        Optional<AppUser> found = appUserRepository.findLockedByEmail(email);

        if (found.isEmpty()) {
            // Khong bao loi ra man hinh - xem giai thich o AuthService. Chi ghi log de nguoi
            // phat trien biet co ai do go nham email, khong lo thong tin ra nguoi dung.
            log.info("Co yeu cau dat lai mat khau cho email chua dang ky, bo qua.");
            return;
        }

        AppUser appUser = found.get();
        if (!appUser.isEnabled() || !allowsPublicOtp(appUser)) {
            // Tai khoan chua kich hoat thi phai di duong dang ky/OTP kich hoat, khong phai duong nay.
            // Neu van gui OTP o day, ma do co the bi dung nguoc lai de kich hoat tai khoan chua xac thuc.
            log.info("Co yeu cau dat lai mat khau cho tai khoan chua kich hoat, bo qua.");
            return;
        }

        String otp = generateOtp();
        otpPolicy.reserveSend(appUser); otpPolicy.issue(appUser, otp);
        appUserRepository.save(appUser);

        sendOtpEmail(email, otp, "Mã đặt lại mật khẩu", "đặt lại mật khẩu");
    }

    @Override
    @Transactional
    public boolean resetPassword(String email, String otpCode, String newRawPassword) {
        email = PublicAccountValidation.email(email); PublicAccountValidation.requireEmail(email);
        PublicAccountValidation.requirePassword(newRawPassword);
        Optional<AppUser> found = appUserRepository.findLockedByEmail(email);
        if (found.isEmpty()) {
            return false;
        }

        AppUser appUser = found.get();
        if (!appUser.isEnabled() || !allowsPublicOtp(appUser)) {
            return false;
        }
        int previousAttempts=appUser.getOtpAttempts();
        if (!otpPolicy.consume(appUser, otpCode)) {
            if(previousAttempts!=appUser.getOtpAttempts()) appUserRepository.save(appUser); return false;
        }

        appUser.setPassword(passwordEncoder.encode(newRawPassword));
        // Xoa OTP ngay sau khi dung - moi ma chi doi mat khau duoc dung mot lan.
        appUser.revokeSessions();
        emailChanges.deleteByUser(appUser);
        appUserRepository.save(appUser);
        return true;
    }

    // ----- Phần dùng chung trong service -----

    @Override @Transactional
    public void resendRegistrationOtp(String username) {
        if(username==null || !username.strip().matches(PublicAccountValidation.USERNAME)) return;
        var found=appUserRepository.findLockedByUsername(username.strip());
        if(found.isEmpty() || found.get().isEnabled() || !allowsPublicOtp(found.get())) return;
        var user=found.get(); String otp=generateOtp();
        otpPolicy.reserveSend(user); otpPolicy.issue(user,otp); appUserRepository.save(user);
        sendOtpEmail(user.getEmail(),otp,"Mã xác thực đăng ký","kích hoạt tài khoản");
    }

    private boolean allowsPublicOtp(AppUser user) {
        return !user.isLockedByAdmin() && (user.getRole() == AppUser.Role.USER || user.getRole() == AppUser.Role.SHIPPER);
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
