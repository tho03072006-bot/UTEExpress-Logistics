package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;

/** Phần địa chỉ có cấu trúc; địa chỉ cũ chưa có dòng phụ phải chọn lại khu vực khi dùng. */
@Entity
@Table(name = "saved_address_area", uniqueConstraints = @UniqueConstraint(name = "uk_saved_address_area", columnNames = "saved_address_id"))
public class SavedAddressArea {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @JoinColumn(name = "saved_address_id", nullable = false, foreignKey = @ForeignKey(name = "fk_saved_area_address")) private SavedAddress savedAddress;
    @Nationalized @Column(nullable = false, length = 10) private String provinceCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_code", referencedColumnName = "area_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_saved_area_area")) private ProvinceArea area;
    protected SavedAddressArea() { }
    public SavedAddressArea(SavedAddress savedAddress, ProvinceArea area) {
        this.savedAddress = savedAddress; this.area = area; provinceCode = area.getProvinceCode();
    }
    public Long getId() { return id; }
    public SavedAddress getSavedAddress() { return savedAddress; }
    public String getProvinceCode() { return provinceCode; }
    public ProvinceArea getArea() { return area; }
    public void configure(ProvinceArea value) { area = value; provinceCode = value.getProvinceCode(); }
}
