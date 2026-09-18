package vn.edu.hcmute.uteexpress.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.EmailChangeRequest;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.EmailChangeRequestRepository;
import vn.edu.hcmute.uteexpress.service.EmailChangeService;
import vn.edu.hcmute.uteexpress.util.MailFromNameSetter;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class EmailChangeServiceImpl implements EmailChangeService {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Hạn dùng của mã, đặt bằng OTP đăng ký / quên mật khẩu cho người dùng khỏi phải nhớ hai mốc. */
    private static final int OTP_EXPIRY_MINUTES = 10;

    /** Số lần nhập sai tối đa trước khi huỷ yêu cầu và bắt gửi lại mã. */
    private static final int MAX_ATTEMPTS = 5;

    private final EmailChangeRequestRepository emailChangeRequestRepository;
    private final AppUserRepository appUserRepository;
    private final JavaMailSender mailSender;
    private final MailFromNameSetter mailFromNameSetter;

    public EmailChangeServiceImpl(EmailChangeRequestRepository emailChangeRequestRepository,
                                  AppUserRepository appUserRepository,
                                  JavaMailSender mailSender,
                                  MailFromNameSetter mailFromNameSetter) {
        this.emailChangeRequestRepository = emailChangeRequestRepository;
        this.appUserRepository = appUserRepository;
        this.mailSender = mailSender;
        this.mailFromNameSetter = mailFromNameSetter;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailChangeRequest> findPending(String username) {
        return emailChangeRequestRepository.findByUser(requireUser(username));
    }

    @Override
    public void requestChange(String username, String newEmail) {
        AppUser appUser = requireUser(username);
        String target = newEmail.trim();

        if (target.equalsIgnoreCase(appUser.getEmail())) {
            throw new IllegalStateException("Email mới trùng với email đang dùng.");
        }
        requireEmailAvailable(target, appUser);

        // Mỗi người chỉ giữ một yêu cầu: đổi ý gõ địa chỉ khác thì ghi đè lên yêu cầu cũ,
        // tránh tình trạng còn tồn nhiều mã cùng hiệu lực cho nhiều địa chỉ khác nhau.
        EmailChangeRequest request = emailChangeRequestRepository.findByUser(appUser)
                .orElseGet(() -> new EmailChangeRequest());
        request.setUser(appUser);
        request.setNewEmail(target);
        applyFreshOtp(request);
        emailChangeRequestRepository.save(request);

        sendOtpEmail(target, request.getOtpCode());
    }

    @Override
    public void resendOtp(String username) {
        AppUser appUser = requireUser(username);
        EmailChangeRequest request = requirePending(appUser);

        // Cấp mã hoàn toàn mới thay vì gửi lại mã cũ: nếu email lần trước lỡ lọt vào tay
        // người khác thì mã trong đó cũng hết tác dụng ngay.
        applyFreshOtp(request);
        emailChangeRequestRepository.save(request);

        sendOtpEmail(request.getNewEmail(), request.getOtpCode());
    }

    @Override
    public void confirm(String username, String otpCode) {
        AppUser appUser = requireUser(username);
        EmailChangeRequest request = requirePending(appUser);

        if (request.isExpired()) {
            emailChangeRequestRepository.delete(request);
            throw new IllegalStateException("Mã xác thực đã hết hạn. Vui lòng yêu cầu đổi email lại từ đầu.");
        }

        if (!request.getOtpCode().equals(otpCode == null ? null : otpCode.trim())) {
            request.setAttemptCount(request.getAttemptCount() + 1);
            request.setUpdatedAt(LocalDateTime.now());
            if (request.getAttemptCount() >= MAX_ATTEMPTS) {
                emailChangeRequestRepository.delete(request);
                throw new IllegalStateException(
                        "Bạn đã nhập sai mã quá " + MAX_ATTEMPTS + " lần. Yêu cầu đổi email đã bị huỷ, "
                                + "vui lòng thực hiện lại từ trang tài khoản.");
            }
            emailChangeRequestRepository.save(request);
            throw new IllegalStateException("Mã xác thực không đúng. Bạn còn "
                    + (MAX_ATTEMPTS - request.getAttemptCount()) + " lần thử.");
        }

        // Kiểm tra trùng lần nữa ngay trước khi ghi: trong 10 phút chờ, một người khác
        // hoàn toàn có thể vừa đăng ký tài khoản bằng đúng địa chỉ này.
        requireEmailAvailable(request.getNewEmail(), appUser);

        appUser.setEmail(request.getNewEmail());
        appUserRepository.save(appUser);
        emailChangeRequestRepository.delete(request);
    }

    @Override
    public void cancel(String username) {
        emailChangeRequestRepository.findByUser(requireUser(username))
                .ifPresent(emailChangeRequestRepository::delete);
    }

    // ----- Phần dùng chung trong service -----

    private void applyFreshOtp(EmailChangeRequest request) {
        request.setOtpCode(generateOtp());
        request.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        request.setAttemptCount(0);
        request.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Chặn trước lỗi ràng buộc unique của cột app_user.email. Phép so sánh bắt được cả
     * khác biệt hoa thường là nhờ collation mặc định của SQL Server, không phải do đoạn
     * Java này - và chính ràng buộc unique cũng dựa trên collation đó nên hai bên khớp nhau.
     */
    private void requireEmailAvailable(String email, AppUser self) {
        Optional<AppUser> owner = appUserRepository.findByEmail(email);
        if (owner.isPresent() && !owner.get().getId().equals(self.getId())) {
            throw new IllegalStateException("Email này đã được một tài khoản khác sử dụng.");
        }
    }

    private EmailChangeRequest requirePending(AppUser appUser) {
        return emailChangeRequestRepository.findByUser(appUser)
                .orElseThrow(() -> new IllegalStateException("Không có yêu cầu đổi email nào đang chờ xác thực."));
    }

    private AppUser requireUser(String username) {
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."));
    }

    /** Gửi mã tới địa chỉ mới; lỗi SMTP không được để lộ OTP trong log. */
    private void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            mailFromNameSetter.applyTo(message);
            message.setTo(toEmail);
            message.setSubject("[UTEExpress] Mã xác thực đổi địa chỉ email");
            message.setText("Chào bạn,\n\n"
                    + "Mã OTP để xác nhận đổi email của bạn là: " + otp + "\n"
                    + "Mã có hiệu lực trong " + OTP_EXPIRY_MINUTES + " phút.\n\n"
                    + "Nếu không phải bạn yêu cầu, hãy bỏ qua email này - "
                    + "địa chỉ email của tài khoản sẽ không bị thay đổi.\n\n"
                    + "Trân trọng,\n"
                    + "UTEExpress");
            mailSender.send(message);
        } catch (Exception ex) {
            // Để transaction rollback yêu cầu đổi email khi không gửi được mã.
            log.warn("Khong gui duoc email xac thuc doi dia chi: {}",
                    ex.getClass().getSimpleName());
            throw new IllegalStateException("Không gửi được email OTP. Vui lòng thử lại sau.");
        }
    }

    private String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
