package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Yêu cầu đổi email đang chờ xác thực OTP - TV1 phụ trách.
 *
 * Vì sao phải có bảng riêng thay vì tái sử dụng otp_code / otp_expiry sẵn có trong
 * AppUser: hai cột đó đang được dùng cho OTP đăng ký và OTP quên mật khẩu, nếu dùng
 * chung thì hai luồng sẽ ghi đè mã của nhau. Quan trọng hơn, luồng này còn phải giữ
 * tạm ĐỊA CHỈ EMAIL MỚI trong lúc chờ xác thực - muốn nhét vào AppUser thì phải thêm
 * cột, mà AppUser.java là entity dùng chung cả nhóm nên không được sửa. Tách bảng
 * riêng trỏ ngược lại bằng @OneToOne là cách đã dùng cho SavedAddress, OrderPayment...
 *
 * Email mới chỉ được ghi đè vào app_user.email SAU KHI người dùng nhập đúng mã OTP
 * gửi tới chính hộp thư mới đó - tức là đã chứng minh mình mở được hộp thư ấy.
 */
@Entity
@Table(name = "email_change_request")
public class EmailChangeRequest {

    /** Mỗi người dùng chỉ được giữ một yêu cầu đang chờ; yêu cầu mới ghi đè yêu cầu cũ. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    /**
     * Địa chỉ email người dùng muốn đổi sang.
     *
     * KHÔNG đánh @Nationalized dù quy ước chung là mọi cột chữ đều phải có: địa chỉ
     * email chỉ gồm ký tự ASCII, và cột app_user.email cũng đang là varchar - để hai
     * cột cùng kiểu thì khi đối chiếu trùng lặp SQL Server khỏi phải ép kiểu.
     */
    @Column(name = "new_email", nullable = false, length = 100)
    private String newEmail;

    @Column(name = "otp_code", nullable = false, length = 10)
    private String otpCode;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Số lần nhập sai mã. Mã chỉ có 6 chữ số nên nếu cho gõ thoải mái thì về lý thuyết
     * có thể dò được; quá số lần cho phép thì Service huỷ luôn yêu cầu, người dùng phải
     * bấm gửi lại mã mới.
     */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public EmailChangeRequest() {
    }

    public EmailChangeRequest(AppUser user, String newEmail, String otpCode, LocalDateTime expiresAt) {
        this.user = user;
        this.newEmail = newEmail;
        this.otpCode = otpCode;
        this.expiresAt = expiresAt;
    }

    /** Mã đã quá hạn chưa - so với thời điểm gọi. */
    public boolean isExpired() {
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now());
    }

    // ----- Getter / Setter -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
