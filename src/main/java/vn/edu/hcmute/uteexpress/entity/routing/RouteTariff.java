package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Nationalized;
import vn.edu.hcmute.uteexpress.entity.Order;

/** Phụ phí và thời gian tuyến có hướng; không có biểu giá Hỏa tốc liên tỉnh. */
@Entity
@Table(name = "route_tariff", uniqueConstraints = @UniqueConstraint(name = "uk_route_tariff", columnNames = {"origin_branch_id", "dest_branch_id", "service_type"}))
@Check(constraints = "surcharge >= 0 and (origin_branch_id = dest_branch_id or service_type <> 'SUPER_EXPRESS')")
public class RouteTariff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tariff_origin")) private Branch originBranch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dest_branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tariff_dest")) private Branch destBranch;
    @Nationalized @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Order.ServiceType serviceType;
    @Column(nullable = false, precision = 12, scale = 0) private BigDecimal surcharge;
    @Nationalized @Column(nullable = false, length = 100) private String leadTimeText;
    @Version @Column(nullable = false) private long revision;
    protected RouteTariff() { }
    public RouteTariff(Branch origin, Branch destination, Order.ServiceType serviceType, BigDecimal surcharge, String leadTimeText) {
        originBranch = origin; destBranch = destination; this.serviceType = serviceType;
        configure(surcharge, leadTimeText);
    }
    public void configure(BigDecimal surcharge, String leadTimeText) { this.surcharge = surcharge; this.leadTimeText = leadTimeText; }
    public Long getId() { return id; }
    public Branch getOriginBranch() { return originBranch; }
    public Branch getDestBranch() { return destBranch; }
    public Order.ServiceType getServiceType() { return serviceType; }
    public BigDecimal getSurcharge() { return surcharge; }
    public String getLeadTimeText() { return leadTimeText; }
    public long getRevision() { return revision; }
}
