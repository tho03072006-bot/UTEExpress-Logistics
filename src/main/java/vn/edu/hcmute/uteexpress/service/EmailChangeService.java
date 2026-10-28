package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.entity.EmailChangeRequest;

import java.util.Optional;

/**
 * Đổi email có xác thực OTP - TV1 phụ trách.
 *
 * Email là đường khôi phục tài khoản duy nhất (quên mật khẩu gửi OTP về đó), nên không
 * cho đổi thẳng. Người dùng phải nhập được mã gửi tới HỘP THƯ MỚI thì mới đổi - gõ nhầm
 * một địa chỉ không tồn tại thì đơn giản là không nhận được mã, email cũ vẫn giữ nguyên
 * và tài khoản không bị mất đường khôi phục.
 */
public interface EmailChangeService {

    /** Yêu cầu đổi email đang chờ xác thực của người dùng, nếu có. */
    Optional<EmailChangeRequest> findPending(String username);

    /**
     * Tạo yêu cầu đổi email và gửi mã OTP tới địa chỉ mới.
     * Yêu cầu cũ (nếu có) bị thay thế. Ném IllegalStateException nếu email mới trùng
     * email đang dùng hoặc đã thuộc về tài khoản khác.
     */
    void requestChange(String username, String newEmail);

    /**
     * Xác nhận mã OTP và ghi email mới vào tài khoản.
     * Ném IllegalStateException nếu không có yêu cầu nào, mã sai, mã hết hạn,
     * nhập sai quá số lần cho phép, hoặc email mới vừa bị tài khoản khác đăng ký mất.
     */
    void confirm(String username, String otpCode);

    /** Gửi lại mã mới cho yêu cầu đang chờ (mã cũ hết hiệu lực). */
    void resendOtp(String username);

    /** Huỷ yêu cầu đang chờ, giữ nguyên email cũ. */
    void cancel(String username);
}
