package vn.edu.hcmute.uteexpress.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.service.AuthService;

import java.security.SecureRandom;
import java.time.LocalDateTime;

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
    public void register(String username, String rawPassword, String email, String fullName) {
        if (appUserRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Ten dang nhap da ton tai");
        }
        if (appUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email da duoc dang ky");
        }

        AppUser appUser = new AppUser(username, passwordEncoder.encode(rawPassword), email);
        appUser.setFullName(fullName);
        appUser.setRole(AppUser.Role.USER);
        appUser.setEnabled(false); // chi bat len sau khi xac thuc OTP

        String otp = generateOtp();
        appUser.setOtpCode(otp);
        appUser.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));

        appUserRepository.save(appUser);
        sendOtpEmail(email, otp);
    }

    @Override
    public boolean verifyOtp(String username, String otpCode) {
        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay tai khoan"));

        boolean valid = otpCode != null
                && otpCode.equals(appUser.getOtpCode())
                && appUser.getOtpExpiry() != null
                && appUser.getOtpExpiry().isAfter(LocalDateTime.now());

        if (valid) {
            appUser.setEnabled(true);
            appUser.setOtpCode(null);
            appUser.setOtpExpiry(null);
            appUserRepository.save(appUser);
        }
        return valid;
    }

    /**
     * Gui OTP qua email that (can dien spring.mail.* trong application.properties).
     * Demo/dev: neu chua cau hinh SMTP that, gui se loi - bat loi va IN OTP RA CONSOLE
     * de van test dang ky duoc ma khong can email that.
     */
    private void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("[UTEExpress] Ma xac thuc dang ky");
            message.setText("Ma OTP cua ban la: " + otp + " (het han sau " + OTP_EXPIRY_MINUTES + " phut)");
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Khong gui duoc email that (chua cau hinh SMTP that trong application.properties). "
                    + "Dung OTP nay de test: {}", otp);
        }
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
