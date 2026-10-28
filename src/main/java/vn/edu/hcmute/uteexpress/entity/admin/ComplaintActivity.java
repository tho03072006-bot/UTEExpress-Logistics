package vn.edu.hcmute.uteexpress.entity.admin;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.OrderComplaint;

/** Lịch sử trao đổi chỉ thêm, không có API sửa/xóa. */
@Entity @Table(name = "complaint_activity", indexes = @Index(name = "idx_complaint_activity_case", columnList = "complaint_id,created_at"))
public class ComplaintActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "complaint_id", nullable = false) private OrderComplaint complaint;
    @Column(nullable = false, length = 50) private String actor;
    @Column(nullable = false, length = 30) private String action;
    @Column(nullable = false, length = 2000) private String message;
    @Column(nullable = false) private LocalDateTime createdAt;
    protected ComplaintActivity() { }
    public ComplaintActivity(OrderComplaint complaint, String actor, String action, String message, LocalDateTime now) {
        this.complaint = complaint; this.actor = actor; this.action = action; this.message = message; createdAt = now;
    }
    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
