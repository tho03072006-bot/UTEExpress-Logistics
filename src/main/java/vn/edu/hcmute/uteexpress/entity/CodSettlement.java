package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

/**
 * Phiếu Shipper nộp tiền cước COD đã thu cho Manager.
 * Bảng riêng (cod_settlement) để không đổi cấu trúc order_payment/shipment_order dùng chung.
 */
@Entity
@Table(name = "cod_settlement", indexes = {
        @Index(name = "idx_cod_settlement_shipper_time", columnList = "shipper_id,submitted_at"),
        @Index(name = "idx_cod_settlement_status_time", columnList = "status,submitted_at")})
public class CodSettlement {
    public enum Status {
        SUBMITTED("Chờ xác nhận"), CONFIRMED("Đã xác nhận"), REJECTED("Bị từ chối");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version @Column(nullable = false)
    private long revision;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipper_id", nullable = false)
    private AppUser shipper;
    @Column(name = "order_count", nullable = false)
    private int orderCount;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount;
    @Column(name = "declared_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal declaredAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;
    @Nationalized @Column(name = "shipper_note", length = 500)
    private String shipperNote;
    @Nationalized @Column(name = "manager_note", length = 500)
    private String managerNote;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected CodSettlement() { }
    public CodSettlement(AppUser shipper, int orderCount, BigDecimal totalAmount, BigDecimal declaredAmount,
            String shipperNote, LocalDateTime submittedAt) {
        this.shipper = shipper; this.orderCount = orderCount; this.totalAmount = totalAmount;
        this.declaredAmount = declaredAmount; this.shipperNote = shipperNote; this.submittedAt = submittedAt;
    }
    public void review(AppUser manager, boolean approved, String note, LocalDateTime now) {
        this.status = approved ? Status.CONFIRMED : Status.REJECTED;
        this.reviewedBy = manager; this.managerNote = note; this.reviewedAt = now;
    }
    /** Chênh lệch = tiền nộp - tổng cước COD; âm nghĩa là nộp thiếu. */
    public BigDecimal getDifference() { return declaredAmount.subtract(totalAmount); }
    public boolean hasDifference() { return declaredAmount.compareTo(totalAmount) != 0; }
    public Long getId() { return id; }
    public long getRevision() { return revision; }
    public AppUser getShipper() { return shipper; }
    public int getOrderCount() { return orderCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDeclaredAmount() { return declaredAmount; }
    public Status getStatus() { return status; }
    public String getShipperNote() { return shipperNote; }
    public String getManagerNote() { return managerNote; }
    public AppUser getReviewedBy() { return reviewedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
}
