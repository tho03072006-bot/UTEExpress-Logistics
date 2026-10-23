package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/** Khu vực chính/phụ của shipper; service giữ một khu vực chính cùng chi nhánh. */
@Entity
@Table(name = "shipper_zone", uniqueConstraints = @UniqueConstraint(name = "uk_shipper_zone", columnNames = {"shipper_id", "area_code"}))
public class ShipperZone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @JoinColumn(name = "shipper_id", nullable = false, foreignKey = @ForeignKey(name = "fk_zone_shipper")) private AppUser shipper;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_code", referencedColumnName = "area_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_zone_area")) private ProvinceArea area;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    protected ShipperZone() { }
    public ShipperZone(AppUser shipper, ProvinceArea area, boolean primary) { this.shipper = shipper; this.area = area; this.primary = primary; }
    public Long getId() { return id; }
    public AppUser getShipper() { return shipper; }
    public ProvinceArea getArea() { return area; }
    public boolean isPrimary() { return primary; }
}
