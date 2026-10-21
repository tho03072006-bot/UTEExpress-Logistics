package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Giữ dấu vết token cả sau khi xóa/xác nhận đơn nháp; không cascade xóa lịch sử. */
@Entity
@Table(name = "order_form_submission", uniqueConstraints = @UniqueConstraint(name = "uk_order_form_token", columnNames = "token"))
public class OrderFormSubmission {
    public enum Purpose { DIRECT, DRAFT }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 36)
    private String token;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_form_user"))
    private AppUser user;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(nullable = false, length = 10)
    private String purpose;
    // ID ảnh chụp, không FK tới draft_order: đơn nháp được xóa sau khi chuyển thành đơn thật.
    @Column(name = "resource_id")
    private Long resourceId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    protected OrderFormSubmission() { }
    public OrderFormSubmission(String token, AppUser user, String requestHash, Purpose purpose, LocalDateTime createdAt) {
        this.token = token; this.user = user; this.requestHash = requestHash;
        this.purpose = purpose.name(); this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public String getToken() { return token; }
    public AppUser getUser() { return user; }
    public String getRequestHash() { return requestHash; }
    public String getPurpose() { return purpose; }
    public Long getResourceId() { return resourceId; }
    public void bindResource(Long value) {
        if (value == null || value <= 0 || (resourceId != null && !resourceId.equals(value)))
            throw new IllegalStateException("Kết quả biểu mẫu không hợp lệ hoặc đã được sử dụng.");
        resourceId = value;
    }
}
