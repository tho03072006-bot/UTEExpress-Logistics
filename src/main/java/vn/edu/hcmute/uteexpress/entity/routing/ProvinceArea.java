package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Nationalized;

/** Khu vực giao nhận nội bộ đặt theo địa danh cũ, không phải cấp hành chính hiện hành.
 * Tọa độ tâm xấp xỉ, dùng cho sắp lộ trình và tính khoảng cách của đồ án. */
@Entity
@Table(name = "province_area", uniqueConstraints = @UniqueConstraint(name = "uk_area_code", columnNames = "area_code"),
        indexes = @Index(name = "idx_area_branch", columnList = "branch_id"))
@Check(constraints = "lat between -90 and 90 and lng between -180 and 180")
public class ProvinceArea {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Nationalized @Column(nullable = false, length = 10) private String provinceCode;
    @Nationalized @Column(name = "area_code", nullable = false, length = 40) private String areaCode;
    @Nationalized @Column(nullable = false, length = 100) private String areaName;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_area_branch")) private Branch branch;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal lat;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal lng;

    protected ProvinceArea() { }
    public ProvinceArea(String provinceCode, String areaCode, String areaName, Branch branch, BigDecimal lat, BigDecimal lng) {
        this.provinceCode = provinceCode; this.areaCode = areaCode; this.areaName = areaName;
        this.branch = branch; this.lat = lat; this.lng = lng;
    }
    public Long getId() { return id; }
    public String getProvinceCode() { return provinceCode; }
    public String getAreaCode() { return areaCode; }
    public String getAreaName() { return areaName; }
    public Branch getBranch() { return branch; }
    public BigDecimal getLat() { return lat; }
    public BigDecimal getLng() { return lng; }
}
