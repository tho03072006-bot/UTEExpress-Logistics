package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;

/** Tuyến riêng của vận đơn, không bổ sung cột hay trạng thái cho entity dùng chung. */
@Entity
@Table(name = "order_route", uniqueConstraints = @UniqueConstraint(name = "uk_order_route_order", columnNames = "order_id"),
        indexes = {@Index(name = "idx_route_origin_stage", columnList = "origin_branch_id,stage"),
                   @Index(name = "idx_route_dest_stage", columnList = "dest_branch_id,stage")})
public class OrderRoute {
    public enum Stage { PICKUP, ORIGIN_HUB, LINEHAUL, DEST_HUB, LAST_MILE }
    /** COURIER: shipper tới lấy; DROP_OFF: khách tự mang hàng tới quầy chi nhánh gửi. */
    public enum PickupMode { COURIER, DROP_OFF }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_route_order")) private Order order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_route_origin")) private Branch originBranch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dest_branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_route_dest")) private Branch destBranch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_area_code", referencedColumnName = "area_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_route_pickup_area")) private ProvinceArea pickupArea;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_area_code", referencedColumnName = "area_code", nullable = false,
            foreignKey = @ForeignKey(name = "fk_route_delivery_area")) private ProvinceArea deliveryArea;
    @org.hibernate.annotations.Nationalized
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Stage stage = Stage.PICKUP;
    @org.hibernate.annotations.Nationalized @org.hibernate.annotations.ColumnDefault("'COURIER'")
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PickupMode pickupMode = PickupMode.COURIER;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_shipper_id", foreignKey = @ForeignKey(name = "fk_route_pickup_shipper")) private AppUser pickupShipper;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_shipper_id", foreignKey = @ForeignKey(name = "fk_route_delivery_shipper")) private AppUser deliveryShipper;
    /**
     * B9 hoàn hàng liên tỉnh: true khi đơn đi chiều ngược (kho nhận -> chuyến về -> kho gửi -> trả người gửi).
     * Dùng lại các giai đoạn cũ: DEST_HUB = hàng hoàn ở kho nhận, LINEHAUL = đang về, ORIGIN_HUB = đã về kho gửi.
     */
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "return_leg", nullable = false) private boolean returnLeg;
    @Version @Column(nullable = false) private long revision;
    protected OrderRoute() { }
    public OrderRoute(Order order, ProvinceArea pickupArea, ProvinceArea deliveryArea) {
        this.order = order; this.pickupArea = pickupArea; this.deliveryArea = deliveryArea;
        originBranch = pickupArea.getBranch(); destBranch = deliveryArea.getBranch();
    }
    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public Branch getOriginBranch() { return originBranch; }
    public Branch getDestBranch() { return destBranch; }
    public ProvinceArea getPickupArea() { return pickupArea; }
    public ProvinceArea getDeliveryArea() { return deliveryArea; }
    public Stage getStage() { return stage; }
    public AppUser getPickupShipper() { return pickupShipper; }
    public AppUser getDeliveryShipper() { return deliveryShipper; }
    public long getRevision() { return revision; }
    public PickupMode getPickupMode() { return pickupMode; }
    public boolean isDropOff() { return pickupMode == PickupMode.DROP_OFF; }
    /** Chỉ đặt lúc tạo đơn; sau khi đã có shipper hay đã nhập kho thì không đổi nữa. */
    public void choosePickupMode(PickupMode mode) {
        if (stage != Stage.PICKUP || pickupShipper != null) throw new IllegalStateException("Đơn đã được lấy hoặc phân công, không đổi hình thức gửi.");
        pickupMode = mode == null ? PickupMode.COURIER : mode;
    }
    public boolean isInterprovincial() { return !originBranch.getId().equals(destBranch.getId()); }
    public boolean isReturnLeg() { return returnLeg; }
    /** Kho đang giữ hàng hoặc chi nhánh phụ trách chặng hiện tại (chiều ngược thì đổi vai hai kho). */
    public Branch currentBranch() {
        if (returnLeg) return stage == Stage.DEST_HUB ? destBranch : originBranch;
        return stage == Stage.PICKUP || stage == Stage.ORIGIN_HUB ? originBranch : destBranch;
    }
    /** Chi nhánh đích của chuyến trung chuyển kế tiếp. */
    public Branch nextHub() { return returnLeg ? originBranch : destBranch; }
    /** Đơn liên tỉnh giao thất bại, đã có hồ sơ hoàn: hàng quay về kho nhận chờ chuyến về chi nhánh gửi. */
    public void beginReturn() {
        if (!isInterprovincial() || returnLeg || stage != Stage.LAST_MILE)
            throw new IllegalStateException("Chỉ đơn liên tỉnh đang ở chặng giao mới chuyển hoàn qua chuyến trung chuyển ngược.");
        returnLeg = true; stage = Stage.DEST_HUB;
    }
    public void assignShipper(AppUser shipper) {
        if (stage == Stage.PICKUP) {
            pickupShipper = shipper;
            if (!isInterprovincial()) deliveryShipper = shipper;
        } else if (stage == Stage.DEST_HUB) {
            deliveryShipper = shipper; stage = Stage.LAST_MILE;
        } else throw new IllegalArgumentException("Giai đoạn tuyến không cho phép phân công.");
    }
    /** Đơn nội tỉnh: shipper vừa lấy hàng bắt đầu đi giao, không qua kho. */
    public void startLastMile() {
        if (stage != Stage.PICKUP || isInterprovincial()) throw new IllegalStateException("Chỉ đơn nội tỉnh mới đi giao thẳng sau khi lấy hàng.");
        stage = Stage.LAST_MILE;
    }
    /** Đơn liên tỉnh đã được bàn giao vào kho chi nhánh gửi. */
    public void receiveAtOriginHub() {
        if (stage != Stage.PICKUP || !isInterprovincial()) throw new IllegalStateException("Chỉ đơn liên tỉnh chờ lấy hàng mới nhập kho gửi.");
        stage = Stage.ORIGIN_HUB;
    }
    /** Khách tự mang hàng tới quầy: liên tỉnh vào kho gửi chờ xếp chuyến, nội tỉnh chờ phân công shipper giao luôn. */
    public void receiveAtCounter() {
        if (stage != Stage.PICKUP || !isDropOff()) throw new IllegalStateException("Chỉ đơn khách tự mang tới và chưa nhận mới nhận tại quầy.");
        stage = isInterprovincial() ? Stage.ORIGIN_HUB : Stage.DEST_HUB;
    }
    /** Kho gửi xuất chuyến trung chuyển; hàng hoàn thì kho nhận xuất chuyến về. */
    public void departLinehaul() {
        if (stage != (returnLeg ? Stage.DEST_HUB : Stage.ORIGIN_HUB))
            throw new IllegalStateException(returnLeg ? "Hàng hoàn chưa nằm ở kho nhận nên không thể xuất chuyến về."
                    : "Đơn chưa nằm ở kho gửi nên không thể xuất chuyến.");
        stage = Stage.LINEHAUL;
    }
    /** Kho đích của chuyến đã nhận đơn: chiều đi về kho nhận (chờ giao), chiều ngược về kho gửi (chờ trả người gửi). */
    public void arriveFromLinehaul() {
        if (stage != Stage.LINEHAUL) throw new IllegalStateException("Đơn không nằm trên chuyến trung chuyển.");
        stage = returnLeg ? Stage.ORIGIN_HUB : Stage.DEST_HUB;
    }
    /** Kho nhận đã nhận đơn từ chuyến trung chuyển, chờ phân công shipper giao. */
    public void arriveDestHub() {
        if (stage != Stage.LINEHAUL) throw new IllegalStateException("Đơn không nằm trên chuyến trung chuyển.");
        stage = Stage.DEST_HUB;
    }
    /** Hủy đơn ra khỏi chuyến khi chuyến chưa xuất kho: đơn quay lại kho gửi. */
    public void backToOriginHub() {
        if (stage != Stage.LINEHAUL) throw new IllegalStateException("Đơn không nằm trên chuyến trung chuyển.");
        stage = Stage.ORIGIN_HUB;
    }
}
