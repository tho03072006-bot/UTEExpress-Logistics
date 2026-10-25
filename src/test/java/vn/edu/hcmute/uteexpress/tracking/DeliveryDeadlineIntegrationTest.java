package vn.edu.hcmute.uteexpress.tracking;

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
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService.Alert;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cảnh báo "Quá hạn giao" theo đúng thời gian dự kiến của tuyến và "Chậm lấy" không tính đơn tự mang ra chi nhánh.
 * Chạy trên H2 in-memory với biểu giá tuyến thật (BranchSeedData); không ghi Aiven.
 */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:delivery-deadline-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-delivery-deadline-uploads"
})
@ActiveProfiles("isolated-logistics") @Transactional
@Import(DeliveryDeadlineIntegrationTest.TestClock.class)
class DeliveryDeadlineIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock deadlineClock() { return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired BranchRepository branches;
    @Autowired OrderRouteRepository routes;
    @Autowired OrderService orderService;
    @Autowired OperationsOverviewService overview;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired LinehaulTripRepository trips;
    @Autowired LinehaulItemRepository tripItems;
    @Autowired OrderComplaintRepository complaints;
    @Autowired vn.edu.hcmute.uteexpress.repository.admin.OperationsCaseRepository cases;
    AppUser customer, managerSgn;

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        customer = account("deadline-customer", AppUser.Role.USER);
        managerSgn = account("deadline-manager-sgn", AppUser.Role.MANAGER); internal(managerSgn);
        var admin = account("deadline-admin", AppUser.Role.ADMIN); internal(admin);
        staffBranches.save(new StaffBranch(managerSgn, branches.findByCode("SGN").orElseThrow(), admin, NOW));
    }

    @Test void intraprovinceStandardIsLateAfterThePromisedTwoDaysNotTheGenericFiveDays() {
        // Tiêu chuẩn nội tỉnh SGN hứa "1–2 ngày": quá 48 giờ là trễ, dù chính sách chung cho tới 120 giờ.
        var late = inFlight("SGN-Q1", Order.ServiceType.STANDARD, 50);
        var onTime = inFlight("SGN-Q1", Order.ServiceType.STANDARD, 40);
        var alerts = overview.alerts(managerSgn.getUsername(), Alert.LATE_DELIVERY, 0).getContent();
        assertTrue(alerts.contains(late)); assertFalse(alerts.contains(onTime));
    }

    @Test void interprovinceExpressIsNotLateBeforeThePromisedThreeDays() {
        // Giao nhanh SGN → HAN hứa "2–3 ngày": 60 giờ chưa trễ (chính sách chung 48 giờ từng báo nhầm), 73 giờ là trễ.
        var onTime = inFlight("HAN-CAUGIAY", Order.ServiceType.EXPRESS, 60);
        var late = inFlight("HAN-CAUGIAY", Order.ServiceType.EXPRESS, 73);
        var page = overview.alerts(managerSgn.getUsername(), Alert.LATE_DELIVERY, 0);
        assertFalse(page.getContent().contains(onTime)); assertTrue(page.getContent().contains(late));
        assertEquals(page.getContent().size(), page.getTotalElements(), "Tổng số đếm theo hạn từng tuyến, khớp số dòng hiển thị");
    }

    @Test void legacyOrderWithoutRouteKeepsTheOperationsPolicyHours() {
        var waiting = inFlight("SGN-Q1", Order.ServiceType.STANDARD, 100);
        var late = inFlight("SGN-Q1", Order.ServiceType.STANDARD, 121);
        for (var order : new Order[]{waiting, late}) routes.delete(routes.findByOrder_Id(order.getId()).orElseThrow());
        routes.flush();
        var alerts = overview.alerts(managerSgn.getUsername(), Alert.LATE_DELIVERY, 0).getContent();
        assertFalse(alerts.contains(waiting)); assertTrue(alerts.contains(late));
    }

    @Test void deliveredOrdersAreNeverLate() {
        var done = inFlight("SGN-Q1", Order.ServiceType.STANDARD, 200);
        done.setStatus(Order.OrderStatus.DELIVERED); orders.flush();
        assertFalse(overview.alerts(managerSgn.getUsername(), Alert.LATE_DELIVERY, 0).getContent().contains(done));
    }

    @Test void deadlineCountsFromPickupNotFromCreation() {
        // Tạo 60 giờ trước nhưng 10 giờ trước mới nhận tại quầy: hạn 48 giờ của Tiêu chuẩn nội tỉnh còn xa.
        var receivedLate = inFlight("SGN-Q1", Order.ServiceType.STANDARD, 60);
        history.saveAndFlush(new OrderStatusHistory(receivedLate, managerSgn, Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.PICKED_UP,
                null, "Nhận hàng tại quầy", null, NOW.minusHours(10)));
        var pickedLongAgo = inFlight("SGN-Q1", Order.ServiceType.STANDARD, 60);
        history.saveAndFlush(new OrderStatusHistory(pickedLongAgo, managerSgn, Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.PICKED_UP,
                null, "Shipper đã lấy hàng", null, NOW.minusHours(50)));
        var alerts = overview.alerts(managerSgn.getUsername(), Alert.LATE_DELIVERY, 0).getContent();
        assertFalse(alerts.contains(receivedLate)); assertTrue(alerts.contains(pickedLongAgo));
    }

    @Test void parcelMissingOnArrivedTripOrAlreadyCompensatedLeavesLateDeliveryAlerts() {
        var missing = inFlight("HAN-CAUGIAY", Order.ServiceType.STANDARD, 200);
        var compensated = inFlight("HAN-CAUGIAY", Order.ServiceType.STANDARD, 200);
        var stillLate = inFlight("HAN-CAUGIAY", Order.ServiceType.STANDARD, 200);
        var trip = new LinehaulTrip("TC-KIEMTHU-THIEU", branches.findByCode("SGN").orElseThrow(), branches.findByCode("HAN").orElseThrow(),
                "51A-00001", "Tài xế kiểm thử", "0901234567");
        trip.depart("NP-001", 1, managerSgn, NOW.minusHours(100)); trip.arrive(managerSgn, NOW.minusHours(90), "Thiếu 1 kiện khi nhận chuyến");
        trips.saveAndFlush(trip); tripItems.saveAndFlush(new LinehaulItem(trip, missing));
        var complaint = complaints.saveAndFlush(new OrderComplaint(compensated, "Mất hàng", "Kiện thất lạc trên đường", null, NOW));
        var paid = new vn.edu.hcmute.uteexpress.entity.admin.OperationsCase(vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Kind.COMPENSATION,
                compensated, null, complaint, new java.math.BigDecimal("10000"), null, "Bồi thường hàng thất lạc", managerSgn.getUsername(), NOW);
        paid.review(true, "deadline-admin", "Duyệt chi trả", NOW); paid.complete("CT-001", null, NOW);
        cases.saveAndFlush(paid);
        var alerts = overview.alerts(managerSgn.getUsername(), Alert.LATE_DELIVERY, 0).getContent();
        assertFalse(alerts.contains(missing), "Kiện ghi thiếu theo dõi ở mục Kiện ghi thiếu của trang Kho");
        assertFalse(alerts.contains(compensated), "Đã chi trả xong bồi thường thì hồ sơ đã đóng");
        assertTrue(alerts.contains(stillLate));
    }

    @Test void latePickupCountsCourierOrdersButNotCounterDropOffs() {
        var courier = created("SGN-Q1", OrderRoute.PickupMode.COURIER, 30);
        var dropOff = created("SGN-Q1", OrderRoute.PickupMode.DROP_OFF, 30);
        var alerts = overview.alerts(managerSgn.getUsername(), Alert.LATE_PICKUP, 0).getContent();
        assertTrue(alerts.contains(courier), "Shipper chưa tới lấy sau 24 giờ là chậm");
        assertFalse(alerts.contains(dropOff), "Đơn tự mang ra chi nhánh không có Shipper nào phải đi lấy");
        var waiting = overview.alerts(managerSgn.getUsername(), Alert.DROP_OFF_WAITING, 0).getContent();
        assertTrue(waiting.contains(dropOff), "Khách để quá hạn lấy hàng mà chưa mang tới thì nằm ở nhóm riêng");
        assertFalse(waiting.contains(courier));
        var fresh = created("SGN-Q1", OrderRoute.PickupMode.DROP_OFF, 5);
        assertFalse(overview.alerts(managerSgn.getUsername(), Alert.DROP_OFF_WAITING, 0).getContent().contains(fresh));
        var hub = routes.findByOrder_Id(dropOff.getId()).orElseThrow();
        var view = overview.dropOffWaiting(java.util.List.of(hub, routes.findByOrder_Id(fresh.getId()).orElseThrow()));
        assertEquals(30L, view.hours().get(dropOff.getId())); assertTrue(view.overdue(dropOff.getId())); assertFalse(view.overdue(fresh.getId()));
    }

    private Order inFlight(String deliveryArea, Order.ServiceType service, int hoursAgo) {
        var order = created(deliveryArea, OrderRoute.PickupMode.COURIER, hoursAgo, service);
        order.setStatus(Order.OrderStatus.PICKED_UP); orders.flush();
        return order;
    }

    private Order created(String deliveryArea, OrderRoute.PickupMode mode, int hoursAgo) {
        return created(deliveryArea, mode, hoursAgo, Order.ServiceType.STANDARD);
    }

    private Order created(String deliveryArea, OrderRoute.PickupMode mode, int hoursAgo, Order.ServiceType service) {
        var request = new OrderCreateRequest();
        request.setPickupProvinceCode("79"); request.setPickupAreaCode("SGN-THUDUC");
        request.setDeliveryProvinceCode(deliveryArea.startsWith("HAN") ? "01" : "79"); request.setDeliveryAreaCode(deliveryArea);
        request.setSenderName("Người gửi thử"); request.setSenderAddress("Số 1 đường thử");
        request.setReceiverName("Người nhận thử"); request.setReceiverPhone("0901234567");
        request.setReceiverAddress("Số 2 đường thử"); request.setWeightKg(1.0);
        request.setServiceType(service); request.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        request.setPickupMode(mode);
        var order = orderService.createOrder(request, customer.getUsername());
        order.setCreatedAt(NOW.minusHours(hoursAgo)); orders.flush();
        return order;
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
