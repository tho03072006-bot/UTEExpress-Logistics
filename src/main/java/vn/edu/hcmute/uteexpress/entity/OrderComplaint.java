package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

/** Một hồ sơ khiếu nại cho mỗi vận đơn, ảnh được phục vụ qua endpoint có kiểm tra quyền. */
@Entity
@Table(name = "order_complaint", indexes = @Index(name = "idx_complaint_status_time", columnList = "status,created_at"))
public class OrderComplaint {
    public enum Status {
        OPEN("Chờ xử lý"), IN_PROGRESS("Đang xử lý"), RESOLVED("Đã giải quyết"), REJECTED("Từ chối");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
        public boolean isClosed() { return this == RESOLVED || this == REJECTED; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version @Column(nullable = false)
    private long revision;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
    @Nationalized @Column(nullable = false, length = 100)
    private String subject;
    @Nationalized @Column(nullable = false, length = 2000)
    private String description;
    @Lob @Column(name = "image_bytes", columnDefinition = "mediumblob")
    private byte[] imageBytes;
    @Column(name = "image_asset", length = 255) private String imageAsset;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;
    @Nationalized @Column(name = "manager_reply", length = 2000)
    private String managerReply;
    @Nationalized @Column(name = "shipper_note", length = 1000)
    private String shipperNote;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private AppUser handledBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OrderComplaint() { }
    public OrderComplaint(Order order, String subject, String description, byte[] image, LocalDateTime now) {
        this.order = order; this.subject = subject; this.description = description;
        this.imageBytes = image; this.createdAt = now; this.updatedAt = now;
    }
    public void respond(AppUser manager, Status status, String reply, LocalDateTime now) {
        this.handledBy = manager; this.status = status; this.managerReply = reply; this.updatedAt = now;
    }
    public void supplement(String note, LocalDateTime now) { this.shipperNote = note; this.updatedAt = now; }
    /** Người gửi nhắn tiếp vào hồ sơ đã kết thúc: quay lại "Đang xử lý" (chưa ai nhận thì "Chờ xử lý"). */
    public void reopen(LocalDateTime now) { this.status = handledBy == null ? Status.OPEN : Status.IN_PROGRESS; this.updatedAt = now; }
    public void touch(LocalDateTime now) { this.updatedAt = now; }
    public Long getId() { return id; }
    public long getRevision() { return revision; }
    public Order getOrder() { return order; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public byte[] getImageBytes() { return imageBytes; }
    public String getImageAsset() { return imageAsset; }
    public void setImageAsset(String asset) { imageAsset = asset; imageBytes = null; }
    public boolean isHasImage() { return imageAsset != null || (imageBytes != null && imageBytes.length > 0); }
    public Status getStatus() { return status; }
    public String getManagerReply() { return managerReply; }
    public String getShipperNote() { return shipperNote; }
    public AppUser getHandledBy() { return handledBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
