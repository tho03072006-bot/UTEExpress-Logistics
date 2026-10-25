package vn.edu.hcmute.uteexpress.service.tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperRoute;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy;
import vn.edu.hcmute.uteexpress.entity.routing.ProvinceArea;
import static org.junit.jupiter.api.Assertions.*;

/** Bước 14 (B6): nhóm gấp đứng trước, trong nhóm đi điểm gần trước, xuất phát từ kho. Dữ liệu cố định, không cần DB. */
class ShipperRouteOrderingTest {
    static ProvinceArea area(String code, double lat, double lng) {
        return new ProvinceArea("79", code, code, null, BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
    }
    static final ProvinceArea HUB_THU_DUC = area("SGN-THUDUC", 10.8497, 106.7702);
    static final ProvinceArea BINH_THANH = area("SGN-BINHTHANH", 10.8106, 106.7091);
    static final ProvinceArea QUAN_7 = area("SGN-QUAN7", 10.7340, 106.7218);
    static final ProvinceArea CU_CHI = area("SGN-CUCHI", 10.9733, 106.4932);
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 9, 0);

    @Test void urgentGroupComesFirstEvenWhenFarther() {
        var order = ShipperRouteService.sequence(List.of(
                new ShipperRouteService.Candidate(0, 3, BINH_THANH),
                new ShipperRouteService.Candidate(1, 1, CU_CHI)), HUB_THU_DUC);
        assertEquals(List.of(1, 0), order, "Hỏa tốc ở Củ Chi đi trước đơn thường ở Bình Thạnh dù xa hơn");
    }

    @Test void withinGroupNearestNeighbourStartingFromHub() {
        var candidates = List.of(
                new ShipperRouteService.Candidate(0, 3, QUAN_7),
                new ShipperRouteService.Candidate(1, 3, CU_CHI),
                new ShipperRouteService.Candidate(2, 3, BINH_THANH));
        // Từ kho Thủ Đức: Bình Thạnh gần nhất, rồi từ Bình Thạnh tới Quận 7, cuối cùng Củ Chi.
        assertEquals(List.of(2, 0, 1), ShipperRouteService.sequence(candidates, HUB_THU_DUC));
        // Xuất phát khác thì thứ tự khác: chứng tỏ thuật toán bắt đầu từ kho chứ không theo thứ tự danh sách.
        assertEquals(1, ShipperRouteService.sequence(candidates, area("HUB-CUCHI", 10.98, 106.49)).get(0));
    }

    @Test void nextGroupContinuesFromLastStopAndLegacyOrdersStayLastInOldOrder() {
        var order = ShipperRouteService.sequence(List.of(
                new ShipperRouteService.Candidate(0, 2, null),
                new ShipperRouteService.Candidate(1, 3, QUAN_7),
                new ShipperRouteService.Candidate(2, 3, CU_CHI),
                new ShipperRouteService.Candidate(3, 1, null),
                new ShipperRouteService.Candidate(4, 2, CU_CHI)), HUB_THU_DUC);
        // Nhóm 2 kết thúc ở Củ Chi nên nhóm 3 đi Củ Chi trước rồi mới Quận 7; đơn cũ chưa có khu vực xếp cuối.
        assertEquals(List.of(4, 2, 1, 0, 3), order);
    }

    @Test void equalDistanceKeepsOriginalOrderAndNoHubStillWorks() {
        assertEquals(List.of(0, 1, 2), ShipperRouteService.sequence(List.of(
                new ShipperRouteService.Candidate(0, 3, BINH_THANH),
                new ShipperRouteService.Candidate(1, 3, BINH_THANH),
                new ShipperRouteService.Candidate(2, 3, BINH_THANH)), null));
    }

    @Test void urgencyFollowsServiceAppointmentAndOperationsPolicy() {
        var policy = new OperationsPolicy(); // mặc định: lấy 24 giờ, Tiêu chuẩn 120 giờ, Nhanh 48 giờ
        int standard = vn.edu.hcmute.uteexpress.service.routing.DeliveryDeadlines.policyHours(policy, Order.ServiceType.STANDARD);
        assertEquals(1, ShipperRouteService.urgency(order(Order.ServiceType.SUPER_EXPRESS, 1), false, policy.getSuperExpressHours(), NOW));
        assertEquals(1, ShipperRouteService.urgency(order(Order.ServiceType.STANDARD, 1), true, standard, NOW), "Đến hẹn giao lại");
        assertEquals(2, ShipperRouteService.urgency(order(Order.ServiceType.STANDARD, 100), false, standard, NOW), "Đã dùng hơn 75% của 120 giờ");
        assertEquals(3, ShipperRouteService.urgency(order(Order.ServiceType.STANDARD, 50), false, standard, NOW));
        assertEquals(2, ShipperRouteService.urgency(order(Order.ServiceType.EXPRESS, 20), false, policy.getPickupHours(), NOW), "Chờ lấy dùng hạn lấy hàng 24 giờ");
        assertEquals(3, ShipperRouteService.urgency(order(Order.ServiceType.EXPRESS, 10), false, policy.getPickupHours(), NOW));
    }

    @Test void urgencyUsesRouteLeadTimeWhenKnown() {
        // Tiêu chuẩn nội tỉnh hứa "1–2 ngày" = 48 giờ: 40 giờ đã quá 75%, dù theo 120 giờ của chính sách thì còn sớm.
        assertEquals(2, ShipperRouteService.urgency(order(Order.ServiceType.STANDARD, 40), false, 48, NOW));
        assertEquals(3, ShipperRouteService.urgency(order(Order.ServiceType.STANDARD, 40), false, 120, NOW));
    }

    @Test void firstLegStartsFromHubAddressWhenKnown() {
        var stops = List.of(new ShipperRoute.Stop(1, "1 Võ Văn Ngân, Thủ Đức", List.of()), new ShipperRoute.Stop(2, "268 Lý Thường Kiệt, Quận 10", List.of()));
        var withHub = ShipperRouteService.legs(stops, ShipperRouteService.MAX_STOPS_PER_LEG, "Kho SGN, 1 Võ Văn Ngân");
        assertTrue(withHub.get(0).url().contains("&origin=Kho+SGN"));
        assertFalse(ShipperRouteService.legs(stops).get(0).url().contains("&origin="), "Chưa biết kho thì Google Maps dùng vị trí hiện tại");
    }

    private static Order order(Order.ServiceType type, int hoursAgo) {
        var order = new Order(); order.setServiceType(type); order.setCreatedAt(NOW.minusHours(hoursAgo)); return order;
    }
}
