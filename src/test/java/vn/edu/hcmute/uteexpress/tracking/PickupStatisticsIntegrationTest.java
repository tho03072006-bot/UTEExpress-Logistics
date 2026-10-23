package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsPeriod;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bước 13: shipper lấy đơn liên tỉnh rồi bàn giao kho được tính "đã lấy", không tính "đã giao" hay "đang xử lý". H2, không ghi Aiven. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:pickup-statistics-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-pickup-statistics-uploads"
})
@ActiveProfiles("isolated-logistics") @Transactional
@Import(PickupStatisticsIntegrationTest.TestClock.class)
class PickupStatisticsIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock pickupStatisticsClock() { return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired ShipperZoneRepository zones;
    @Autowired BranchRepository branches;
    @Autowired ProvinceAreaRepository areas;
    @Autowired OrderRouteRepository routes;
    @Autowired OrderService orderService;
    @Autowired DispatchService dispatch;
    @Autowired TrackingService tracking;
    @Autowired ShipperStatisticsService statistics;
    @Autowired OperationsOverviewService overview;
    @Autowired ShipperRouteService shipperRoutes;
    AppUser customer, managerSgn, admin, shipperSgn;

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        customer = account("pickup-customer", AppUser.Role.USER);
        admin = account("pickup-admin", AppUser.Role.ADMIN); internal(admin);
        managerSgn = account("pickup-manager-sgn", AppUser.Role.MANAGER); internal(managerSgn);
        shipperSgn = account("pickup-shipper-sgn", AppUser.Role.SHIPPER);
        var sgn = branches.findByCode("SGN").orElseThrow();
        staffBranches.save(new StaffBranch(managerSgn, sgn, admin, NOW));
        staffBranches.save(new StaffBranch(shipperSgn, sgn, admin, NOW));
        zones.save(new ShipperZone(shipperSgn, areas.findByAreaCode("SGN-THUDUC").orElseThrow(), true));
    }

    @Test void sgnShipperPicksUpSgnToHanAndHandsOverToHub() {
        var order = orderService.createOrder(request(), customer.getUsername());
        dispatch.assignSuggested(order.getTrackingCode(), managerSgn.getUsername());
        assertEquals(shipperSgn.getId(), routes.findByOrder_Id(order.getId()).orElseThrow().getPickupShipper().getId());
        tracking.updateStatus(order.getId(), shipperSgn.getUsername(), Order.OrderStatus.PICKED_UP, null, "Đã lấy hàng kiểm thử", null);
        tracking.handoverToHub(order.getId(), shipperSgn.getUsername());

        var own = statistics.getStatistics(shipperSgn.getUsername(), ShipperStatisticsPeriod.DAY);
        assertEquals(1, own.pickedUpOrders());
        assertEquals(0, own.deliveredOrders());
        assertEquals(0, own.inProgressOrders(), "Bàn giao kho xong thì shipper hết giữ đơn");

        var row = overview.performance(managerSgn.getUsername(), 0).getContent().stream()
                .filter(r -> r.id().equals(shipperSgn.getId())).findFirst().orElseThrow();
        assertEquals(1, row.pickedUp());
        assertEquals(0, row.delivered());
        assertEquals(0, row.getActive());
    }

    @Test void todayRouteStartsFromBranchHubAndVisitsNearestPickupFirst() {
        var far = orderService.createOrder(request("SGN-CUCHI", "SGN-THUDUC", "79", "Số 9 Củ Chi"), customer.getUsername());
        var near = orderService.createOrder(request("SGN-THUDUC", "SGN-Q1", "79", "Số 1 Thủ Đức"), customer.getUsername());
        zones.save(new ShipperZone(shipperSgn, areas.findByAreaCode("SGN-CUCHI").orElseThrow(), false));
        dispatch.assignSuggested(far.getTrackingCode(), managerSgn.getUsername());
        dispatch.assignSuggested(near.getTrackingCode(), managerSgn.getUsername());
        var route = shipperRoutes.today(shipperSgn.getUsername());
        var hub = branches.findByCode("SGN").orElseThrow();
        assertNotNull(route.startHub());
        assertTrue(route.startHub().contains(hub.getName()));
        // Kho SGN đặt ở khu vực Thủ Đức nên điểm lấy ở Thủ Đức đi trước điểm ở Củ Chi dù đơn Củ Chi tạo trước.
        assertEquals(near.getTrackingCode(), route.stops().get(0).tasks().get(0).trackingCode());
        assertEquals(1, route.stops().get(0).number());
        assertTrue(route.legs().get(0).url().contains("&origin="), "Chặng đầu xuất phát từ địa chỉ kho");
    }

    private OrderCreateRequest request() { return request("SGN-THUDUC", "HAN-CAUGIAY", "01", "Số 1 Võ Văn Ngân"); }

    private OrderCreateRequest request(String pickupArea, String deliveryArea, String deliveryProvince, String street) {
        var request = new OrderCreateRequest();
        request.setPickupProvinceCode("79"); request.setPickupAreaCode(pickupArea);
        request.setDeliveryProvinceCode(deliveryProvince); request.setDeliveryAreaCode(deliveryArea);
        request.setSenderName("Người gửi kiểm thử"); request.setSenderAddress(street);
        request.setReceiverName("Người nhận kiểm thử"); request.setReceiverPhone("0901234567"); request.setReceiverAddress("Số 2 Cầu Giấy");
        request.setWeightKg(1.0); request.setServiceType(Order.ServiceType.STANDARD); request.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        return request;
    }
    private AppUser account(String username, AppUser.Role role) {
        var value = new AppUser(username, "test-only-hash", username + "@test.invalid");
        value.setEnabled(true); value.setRole(role); value.setFullName("Tài khoản kiểm thử");
        return users.saveAndFlush(value);
    }
    private StaffAccount internal(AppUser user) {
        var value = new StaffAccount(); value.setUser(user); value.setIssuedBy("test-only"); value.setActive(true); value.setPasswordChangeRequired(false);
        return staff.saveAndFlush(value);
    }
}
