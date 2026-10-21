package vn.edu.hcmute.uteexpress.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/** Gọi khi đã khóa AppUser trong transaction: giới hạn dùng chung giữa các máy của nhóm. */
@Component
public class OtpPolicy {
    public static final int MAX_ATTEMPTS = 5;
    private final Clock clock;
    public OtpPolicy(Clock clock) { this.clock = clock; }
    public LocalDateTime now() { return LocalDateTime.now(clock); }
    public void reserveSend(AppUser user) {
        var now = now();
        if (user.getOtpLastSentAt() != null && now.isBefore(user.getOtpLastSentAt().plusSeconds(60)))
            throw new IllegalStateException("Vui lòng chờ ít nhất 60 giây trước khi yêu cầu mã OTP mới.");
        if (user.getOtpWindowStart() == null || !now.isBefore(user.getOtpWindowStart().plusHours(1))) {
            user.setOtpWindowStart(now); user.setOtpSendCount(0);
        }
        if (user.getOtpSendCount() >= 5) throw new IllegalStateException("Đã đạt giới hạn 5 email OTP trong một giờ. Vui lòng thử lại sau.");
        user.setOtpLastSentAt(now); user.setOtpSendCount(user.getOtpSendCount() + 1);
    }
    public void issue(AppUser user, String code) {
        user.setOtpCode(code); user.setOtpExpiry(now().plusMinutes(10)); user.setOtpAttempts(0);
    }
    public boolean consume(AppUser user, String submitted) {
        if (user.getOtpCode() == null || user.getOtpExpiry() == null || !now().isBefore(user.getOtpExpiry())
                || user.getOtpAttempts() >= MAX_ATTEMPTS) return false;
        boolean matches = submitted != null && submitted.matches("[0-9]{6}")
            && MessageDigest.isEqual(user.getOtpCode().getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            user.setOtpAttempts(user.getOtpAttempts() + 1);
            if (user.getOtpAttempts() >= MAX_ATTEMPTS) { clear(user); user.setOtpAttempts(MAX_ATTEMPTS); }
            return false;
        }
        clear(user); return true;
    }
    public void clear(AppUser user) { user.setOtpCode(null); user.setOtpExpiry(null); user.setOtpAttempts(0); }
}
