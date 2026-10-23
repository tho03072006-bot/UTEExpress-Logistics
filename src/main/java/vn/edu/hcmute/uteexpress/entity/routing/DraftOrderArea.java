package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import vn.edu.hcmute.uteexpress.entity.DraftOrder;

/** Một dòng cho mỗi đơn nháp, lưu riêng tỉnh và khu vực giao nhận của cả hai đầu. */
@Entity
@Table(name = "draft_order_area", uniqueConstraints = @UniqueConstraint(name = "uk_draft_order_area", columnNames = "draft_order_id"))
public class DraftOrderArea {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @JoinColumn(name = "draft_order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_draft_area_order")) private DraftOrder draftOrder;
    @Nationalized @Column(nullable = false, length = 10) private String pickupProvinceCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_area_code", referencedColumnName = "area_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_draft_pickup_area")) private ProvinceArea pickupArea;
    @Nationalized @Column(nullable = false, length = 10) private String deliveryProvinceCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_area_code", referencedColumnName = "area_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_draft_delivery_area")) private ProvinceArea deliveryArea;
    protected DraftOrderArea() { }
    public DraftOrderArea(DraftOrder draftOrder, ProvinceArea pickupArea, ProvinceArea deliveryArea) {
        this.draftOrder = draftOrder; this.pickupArea = pickupArea; this.deliveryArea = deliveryArea;
        pickupProvinceCode = pickupArea.getProvinceCode(); deliveryProvinceCode = deliveryArea.getProvinceCode();
    }
    public Long getId() { return id; }
    public DraftOrder getDraftOrder() { return draftOrder; }
    public String getPickupProvinceCode() { return pickupProvinceCode; }
    public ProvinceArea getPickupArea() { return pickupArea; }
    public String getDeliveryProvinceCode() { return deliveryProvinceCode; }
    public ProvinceArea getDeliveryArea() { return deliveryArea; }
    public void configure(ProvinceArea pickup, ProvinceArea delivery) {
        pickupArea = pickup; deliveryArea = delivery; pickupProvinceCode = pickup.getProvinceCode(); deliveryProvinceCode = delivery.getProvinceCode();
    }
}
