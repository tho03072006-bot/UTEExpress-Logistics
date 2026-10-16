package vn.edu.hcmute.uteexpress.entity.admin;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.Order;

/** Phiên bản giá không sửa sau khi tạo; thời gian theo khoảng [bắt đầu, kết thúc). */
@Entity @Table(name = "shipping_tariff_version", indexes = @Index(name = "idx_tariff_version_time", columnList = "service_type,starts_at,ends_at"))
public class ShippingTariffVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private long revision;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Order.ServiceType serviceType;
    @Column(nullable = false, precision = 12, scale = 0) private BigDecimal baseFee;
    @Column(nullable = false, precision = 12, scale = 0) private BigDecimal feePerKg;
    @Column(nullable = false) private LocalDateTime startsAt;
    @Column(nullable = false) private LocalDateTime endsAt;
    @Column(nullable = false) private boolean cancelled;
    @Column(nullable = false, length = 50) private String createdBy;
    @Column(nullable = false, length = 500) private String reason;
    @Column(nullable = false) private LocalDateTime createdAt;
    protected ShippingTariffVersion() { }
    public ShippingTariffVersion(Order.ServiceType type, BigDecimal base, BigDecimal perKg,
            LocalDateTime start, LocalDateTime end, String actor, String reason, LocalDateTime now) {
        serviceType = type; baseFee = base; feePerKg = perKg; startsAt = start; endsAt = end;
        createdBy = actor; this.reason = reason; createdAt = now;
    }
    public void cancel() { cancelled = true; }
    public Long getId() { return id; }
    public long getRevision() { return revision; }
    public Order.ServiceType getServiceType() { return serviceType; }
    public BigDecimal getBaseFee() { return baseFee; }
    public BigDecimal getFeePerKg() { return feePerKg; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public boolean isCancelled() { return cancelled; }
    public String getCreatedBy() { return createdBy; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
