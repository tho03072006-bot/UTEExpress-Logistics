package vn.edu.hcmute.uteexpress.entity.admin;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.Order;

@Entity @Table(name = "shipping_tariff")
public class ShippingTariff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, unique = true, length = 20)
    private Order.ServiceType serviceType;

    @Column(name = "base_fee", nullable = false, precision = 12, scale = 0)
    private BigDecimal baseFee;

    @Column(name = "fee_per_kg", nullable = false, precision = 12, scale = 0)
    private BigDecimal feePerKg;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version @Column(nullable = false)
    private long revision = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Order.ServiceType getServiceType() { return serviceType; }
    public void setServiceType(Order.ServiceType serviceType) { this.serviceType = serviceType; }
    public BigDecimal getBaseFee() { return baseFee; }
    public void setBaseFee(BigDecimal baseFee) { this.baseFee = baseFee; }
    public BigDecimal getFeePerKg() { return feePerKg; }
    public void setFeePerKg(BigDecimal feePerKg) { this.feePerKg = feePerKg; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }

}
