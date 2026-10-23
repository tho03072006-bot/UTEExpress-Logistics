package vn.edu.hcmute.uteexpress.dto.manager;
/**
 * pickedUp: số đơn shipper đã lấy hàng (theo order_route.pickup_shipper_id; đơn cũ chưa có tuyến thì theo shipper giữ đơn).
 * Đơn liên tỉnh bàn giao vào kho thì shipper lấy được tính "đã lấy", còn "đã giao" thuộc shipper chi nhánh nhận.
 */
public record ShipperPerformanceResponse(Long id, String username, String fullName,
        long pending, long inTransit, long delivered, long failed, long returned, long pickedUp) {
    public long getActive() { return pending + inTransit; }
    public java.math.BigDecimal getSuccessRate() {
        return vn.edu.hcmute.uteexpress.util.DeliverySuccessRate.calculate(delivered, returned);
    }
}
