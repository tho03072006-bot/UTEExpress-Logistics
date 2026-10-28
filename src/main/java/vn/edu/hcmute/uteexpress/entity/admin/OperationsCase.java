package vn.edu.hcmute.uteexpress.entity.admin;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.*;

/** Hồ sơ duyệt: không sửa/xóa hồ sơ đã kết thúc; nhật ký ghi cùng transaction. */
@Entity @Table(name = "operations_case", indexes = @Index(name = "idx_ops_case_state", columnList = "kind,status,created_at"))
public class OperationsCase {
    public enum Kind {
        RETRY("Giao lại", "Xử lý hàng"), RETURN("Hoàn hàng về người gửi", "Xử lý hàng"),
        CASH_SUPPLEMENT("Shipper nộp bù tiền thiếu", "Đối soát cước COD với Shipper"),
        CASH_REFUND("Trả lại tiền Shipper nộp thừa", "Đối soát cước COD với Shipper"), COMPENSATION("Bồi thường cho khách", "Chi trả cho khách");
        private final String label; private final String group;
        Kind(String label, String group) { this.label = label; this.group = group; }
        public String getLabel() { return label; }
        /** Nhóm hiển thị trong ô lọc (optgroup). */
        public String getGroup() { return group; }
        public boolean isFinancial() { return this == CASH_SUPPLEMENT || this == CASH_REFUND || this == COMPENSATION; }
    }
    public enum Status {
        REQUESTED("Chờ duyệt"), APPROVED("Đã duyệt"), REJECTED("Từ chối"), IN_PROGRESS("Đang hoàn"), COMPLETED("Đã hoàn tất");
        private final String label; Status(String label) { this.label = label; } public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private long revision;
    @Column(name = "request_token", unique = true, length = 36) private String requestToken;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.REQUESTED;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id") private Order order;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "settlement_id") private CodSettlement settlement;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "complaint_id") private OrderComplaint complaint;
    @Column(precision = 12, scale = 0) private BigDecimal amount;
    private LocalDateTime nextAttemptAt;
    @Column(nullable = false, length = 500) private String reason;
    @Column(nullable = false, length = 50) private String createdBy;
    @Column(length = 50) private String reviewedBy;
    @Column(length = 500) private String reviewNote;
    @Column(length = 100) private String receiptReference;
    @Lob @Column(columnDefinition = "mediumblob") private byte[] evidence;
    @Column(name = "evidence_asset", length = 255) private String evidenceAsset;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    protected OperationsCase() { }
    public OperationsCase(Kind kind, Order order, CodSettlement settlement, OrderComplaint complaint,
            BigDecimal amount, LocalDateTime nextAttemptAt, String reason, String actor, LocalDateTime now) {
        this.kind = kind; this.order = order; this.settlement = settlement; this.complaint = complaint;
        this.amount = amount; this.nextAttemptAt = nextAttemptAt; this.reason = reason;
        createdBy = actor; createdAt = now; updatedAt = now;
    }
    public void review(boolean approve, String actor, String note, LocalDateTime now) {
        status = approve ? Status.APPROVED : Status.REJECTED; reviewedBy = actor; reviewNote = note; updatedAt = now;
    }
    public void start(LocalDateTime now) { status = Status.IN_PROGRESS; updatedAt = now; }
    public void attach(String reference, byte[] image) { receiptReference = reference; evidence = image; }
    public void setRequestToken(String token) { requestToken = token; }
    public void complete(String reference, byte[] image, LocalDateTime now) {
        status = Status.COMPLETED; receiptReference = reference; evidence = image; updatedAt = now;
    }
    public Long getId() { return id; }
    public long getRevision() { return revision; }
    public Kind getKind() { return kind; }
    public Status getStatus() { return status; }
    public Order getOrder() { return order; }
    public CodSettlement getSettlement() { return settlement; }
    public OrderComplaint getComplaint() { return complaint; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getReason() { return reason; }
    public String getCreatedBy() { return createdBy; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewNote() { return reviewNote; }
    public String getReceiptReference() { return receiptReference; }
    public byte[] getEvidence() { return evidence; }
    public String getEvidenceAsset() { return evidenceAsset; }
    public void setEvidenceAsset(String asset) { evidenceAsset = asset; evidence = null; }
    public boolean isHasEvidence() { return evidenceAsset != null || (evidence != null && evidence.length > 0); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getDueAt() {
        // Quy tắc demo: xử lý chênh lệch trong 7 ngày từ khi xác nhận phiếu gốc.
        return settlement != null && settlement.getReviewedAt() != null ? settlement.getReviewedAt().plusDays(7) : null;
    }
}
