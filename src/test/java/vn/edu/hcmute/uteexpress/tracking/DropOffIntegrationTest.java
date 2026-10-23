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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.FeeEstimateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService;
import vn.edu.hcmute.uteexpress.service.routing.*;
import vn.edu.hcmute.uteexpress.service.tracking.DispatchService;
import static org.junit.jupiter.api.Assertions.*;

/** Khách tự mang hàng ra chi nhánh, chạy trên H2 in-memory với địa bàn thật; không ghi Aiven. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:drop-off-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-drop-off-uploads"
})
@ActiveProfiles("isolated-logistics") @Transactional
@Import(DropOffIntegrationTest.TestClock.class)
class DropOffIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock dropOffClock() { return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired BranchRepository branches;
    @Autowired OrderRouteRepository routes;
    @Autowired OrderService orderService;
    @Autowired RouteQuoteService quotes;
    @Autowired DispatchService dispatch;
    @Autowired HubService hub;
    @Autowired OperationsOverviewService overview;
    AppUser customer, managerSgn, managerHan, admin;

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        customer = account("drop-customer", AppUser.Role.USER);
        managerSgn = account("drop-manager-sgn", AppUser.Role.MANAGER); internal(managerSgn);
        managerHan = account("drop-manager-han", AppUser.Role.MANAGER); internal(managerHan);
        admin = account("drop-admin", AppUser.Role.ADMIN); internal(admin);
        staffBranches.save(new StaffBranch(managerSgn, branches.findByCode("SGN").orElseThrow(), admin, NOW));
        staffBranches.save(new StaffBranch(managerHan, branches.findByCode("HAN").orElseThrow(), admin, NOW));
    }

    @Test void estimateAndCreatedOrderChargeTheSameDiscountedFee() {
        var courier = order("HAN-CAUGIAY", OrderRoute.PickupMode.COURIER);
        var dropOff = order("HAN-CAUGIAY", OrderRoute.PickupMode.DROP_OFF);
        assertEquals(courier.getShippingFee().subtract(new BigDecimal("3000")), dropOff.getShippingFee());
        var estimate = new FeeEstimateRequest();
        estimate.setPickupProvinceCode("79"); estimate.setPickupAreaCode("SGN-THUDUC");
        estimate.setDeliveryProvinceCode("01"); estimate.setDeliveryAreaCode("HAN-CAUGIAY");
        estimate.setWeightKg(1.0); estimate.setServiceType(Order.ServiceType.STANDARD); estimate.setPickupMode(OrderRoute.PickupMode.DROP_OFF);
        assertEquals(0, dropOff.getShippingFee().compareTo(quotes.quotes(estimate).get(0).total()));
        assertTrue(routes.findByOrder_Id(dropOff.getId()).orElseThrow().isDropOff());
    }

    @Test void dropOffNeverShowsInDispatchOrUnassignedAlerts() {
        var courier = order("SGN-THUDUC", OrderRoute.PickupMode.COURIER);
        var dropOff = order("SGN-THUDUC", OrderRoute.PickupMode.DROP_OFF);
        var pending = dispatch.pending(managerSgn.getUsername(), null, 0).getContent();
        assertTrue(pending.stream().anyMatch(o -> o.getId().equals(courier.getId())));
        assertFalse(pending.stream().anyMatch(o -> o.getId().equals(dropOff.getId())));
        var unassigned = overview.alerts(managerSgn.getUsername(), OperationsOverviewService.Alert.UNASSIGNED, 0).getContent();
        assertTrue(unassigned.contains(courier)); assertFalse(unassigned.contains(dropOff));
        assertTrue(hub.overview(managerSgn.getUsername(), null).dropOffs().stream().anyMatch(r -> r.getOrder().getId().equals(dropOff.getId())));
        assertEquals(java.util.Set.of(dropOff.getId()), overview.dropOffOrderIds(java.util.List.of(courier, dropOff)));
    }

    @Test void counterReceiptMovesIntraprovincialToDestHubAndInterprovincialToOriginHub() {
        var local = order("SGN-Q1", OrderRoute.PickupMode.DROP_OFF);
        var inter = order("HAN-CAUGIAY", OrderRoute.PickupMode.DROP_OFF);
        hub.receiveAtCounter(managerSgn.getUsername(), local.getId());
        hub.receiveAtCounter(managerSgn.getUsername(), inter.getId());
        assertEquals(Order.OrderStatus.PICKED_UP, local.getStatus()); assertEquals(Order.OrderStatus.PICKED_UP, inter.getStatus());
        assertEquals(OrderRoute.Stage.DEST_HUB, routes.findByOrder_Id(local.getId()).orElseThrow().getStage());
        assertEquals(OrderRoute.Stage.ORIGIN_HUB, routes.findByOrder_Id(inter.getId()).orElseThrow().getStage());
        // Nội tỉnh nhận tại quầy xong vào hàng chờ phân công giao; liên tỉnh nằm trong danh sách chờ xếp chuyến.
        assertTrue(dispatch.pending(managerSgn.getUsername(), null, 0).getContent().stream().anyMatch(o -> o.getId().equals(local.getId())));
        assertTrue(hub.overview(managerSgn.getUsername(), null).waiting().stream().anyMatch(r -> r.getOrder().getId().equals(inter.getId())));
        assertThrows(IllegalStateException.class, () -> hub.receiveAtCounter(managerSgn.getUsername(), local.getId()));
        var timeline = orderService.buildTimeline(local);
        assertTrue(timeline.get(0).getLabel().startsWith("Chờ bạn mang hàng tới chi nhánh"));
        assertTrue(timeline.get(1).getLabel().startsWith("Đã nhận tại quầy"));
    }

    @Test void onlyOriginBranchManagerOrAdminReceivesAndCourierOrdersAreRejected() {
        var dropOff = order("HAN-CAUGIAY", OrderRoute.PickupMode.DROP_OFF);
        var courier = order("HAN-CAUGIAY", OrderRoute.PickupMode.COURIER);
        assertThrows(AccessDeniedException.class, () -> hub.receiveAtCounter(managerHan.getUsername(), dropOff.getId()));
        assertEquals(Order.OrderStatus.PENDING_PICKUP, dropOff.getStatus());
        assertThrows(IllegalStateException.class, () -> hub.receiveAtCounter(managerSgn.getUsername(), courier.getId()));
        hub.receiveAtCounter(admin.getUsername(), dropOff.getId());
        assertEquals(OrderRoute.Stage.ORIGIN_HUB, routes.findByOrder_Id(dropOff.getId()).orElseThrow().getStage());
    }

    @Test void unpaidWalletOrderCannotBeReceivedAtCounter() {
        var request = request("SGN-Q1", OrderRoute.PickupMode.DROP_OFF);
        request.setPaymentMethod(OrderPayment.PaymentMethod.MOMO);
        var order = orderService.createOrder(request, customer.getUsername());
        assertThrows(IllegalArgumentException.class, () -> hub.receiveAtCounter(managerSgn.getUsername(), order.getId()));
        assertEquals(OrderRoute.Stage.PICKUP, routes.findByOrder_Id(order.getId()).orElseThrow().getStage());
    }

    private Order order(String deliveryArea, OrderRoute.PickupMode mode) {
        return orderService.createOrder(request(deliveryArea, mode), customer.getUsername());
    }

    private OrderCreateRequest request(String deliveryArea, OrderRoute.PickupMode mode) {
        var request = new OrderCreateRequest();
        request.setPickupProvinceCode("79"); request.setPickupAreaCode("SGN-THUDUC");
        request.setDeliveryProvinceCode(deliveryArea.startsWith("HAN") ? "01" : "79"); request.setDeliveryAreaCode(deliveryArea);
        request.setSenderName("Người gửi thử"); request.setSenderAddress("Số 1 đường thử");
        request.setReceiverName("Người nhận thử"); request.setReceiverPhone("0901234567");
        request.setReceiverAddress("Số 2 đường thử"); request.setWeightKg(1.0);
        request.setServiceType(Order.ServiceType.STANDARD); request.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        request.setPickupMode(mode);
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
