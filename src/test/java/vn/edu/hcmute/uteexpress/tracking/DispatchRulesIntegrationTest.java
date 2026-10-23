package vn.edu.hcmute.uteexpress.tracking;

import java.time.*;
import java.util.List;
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
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.tracking.DispatchService;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Ràng buộc phân công B5 trên H2 với địa bàn thật: xếp hạng gợi ý theo khu vực chính/phụ/lân cận rồi tải việc,
 * shipper đủ số đơn tối đa không còn trong gợi ý và không gán được, sai chi nhánh bị từ chối. Không ghi Aiven.
 */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:dispatch-rules-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-dispatch-rules-uploads"
})
@ActiveProfiles("isolated-logistics") @Transactional
@Import(DispatchRulesIntegrationTest.TestClock.class)
class DispatchRulesIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock dispatchRulesClock() { return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired ShipperZoneRepository zones;
    @Autowired AreaNeighborRepository neighbors;
    @Autowired BranchRepository branches;
    @Autowired ProvinceAreaRepository areas;
    @Autowired OperationsPolicyRepository policies;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired DispatchService dispatch;
    AppUser customer, admin, managerSgn, managerHan, primary, secondary, neighbour, other, busyPrimary, hanShipper;

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        neighbors.save(new AreaNeighbor(area("SGN-THUDUC"), area("SGN-BINHTHANH")));
        customer = account("rules-customer", AppUser.Role.USER);
        admin = account("rules-admin", AppUser.Role.ADMIN); internal(admin);
        managerSgn = account("rules-manager-sgn", AppUser.Role.MANAGER); internal(managerSgn); branch(managerSgn, "SGN");
        managerHan = account("rules-manager-han", AppUser.Role.MANAGER); internal(managerHan); branch(managerHan, "HAN");
        // Tạo theo thứ tự id ngược với xếp hạng mong đợi để chứng minh không xếp theo id.
        other = shipper("rules-other", "SGN", "SGN-CUCHI", null);
        neighbour = shipper("rules-neighbour", "SGN", "SGN-BINHTHANH", null);
        secondary = shipper("rules-secondary", "SGN", "SGN-Q1", "SGN-THUDUC");
        busyPrimary = shipper("rules-busy-primary", "SGN", "SGN-THUDUC", null);
        primary = shipper("rules-primary", "SGN", "SGN-THUDUC", null);
        hanShipper = shipper("rules-han", "HAN", "HAN-CAUGIAY", null);
        // busyPrimary đang giữ 1 đơn, primary chưa giữ đơn nào: cùng khu vực chính thì người ít việc hơn đứng trước.
        hold(busyPrimary, 1);
    }

    @Test void suggestionRanksPrimaryThenSecondaryThenNeighbourThenOthersAndPrefersLighterLoad() {
        var order = order();
        var ranked = ranked(managerSgn, order);
        assertEquals(List.of(primary.getId(), busyPrimary.getId(), secondary.getId(), neighbour.getId(), other.getId()), ranked);
        assertFalse(ranked.contains(hanShipper.getId()), "Shipper HAN không được gợi ý cho chặng lấy ở SGN");
    }

    @Test void wrongBranchShipperIsRejectedEvenWhenRequestIsForged() {
        var order = order();
        var error = assertThrows(IllegalArgumentException.class, () -> dispatch.assign(order.getTrackingCode(), hanShipper.getId(), 0L,
                order.getUpdatedAt(), "Thử gán shipper HAN cho chặng lấy SGN", managerSgn.getUsername()));
        assertEquals("Shipper phải thuộc đúng chi nhánh của chặng.", error.getMessage());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> ranked(managerHan, order), "Manager HAN không phân công chặng ở SGN");
    }

    @Test void shipperAtCapacityIsNotSuggestedCannotBeAssignedAndBulkReportsNoCapacity() {
        var policy = policies.findAll().get(0); policy.setMaxActiveOrders(1); policies.saveAndFlush(policy);
        var order = order();
        var ranked = ranked(managerSgn, order);
        assertFalse(ranked.contains(busyPrimary.getId()), "Đủ số đơn tối đa thì không còn trong gợi ý");
        assertEquals(primary.getId(), ranked.get(0));
        var error = assertThrows(IllegalArgumentException.class, () -> dispatch.assign(order.getTrackingCode(), busyPrimary.getId(), 0L,
                order.getUpdatedAt(), "Thử gán người đã đủ đơn", managerSgn.getUsername()));
        assertEquals("Shipper đã đủ số đơn tối đa đang giữ.", error.getMessage());
        // Phân công theo gợi ý: mỗi lần gán cộng tải cho người vừa nhận; khi ai cũng đủ đơn thì báo hết sức chứa.
        dispatch.assignSuggested(order.getTrackingCode(), managerSgn.getUsername());
        assertEquals(primary.getId(), orders.findById(order.getId()).orElseThrow().getShipper().getId());
        for (var shipper : List.of(secondary, neighbour, other)) hold(shipper, 1);
        var last = order();
        var full = assertThrows(IllegalArgumentException.class, () -> dispatch.assignSuggested(last.getTrackingCode(), managerSgn.getUsername()));
        assertEquals("Hết shipper còn sức chứa.", full.getMessage());
        assertNull(orders.findById(last.getId()).orElseThrow().getShipper());
    }

    private List<Long> ranked(AppUser manager, Order order) {
        return dispatch.suggestions(manager.getUsername(), List.of(order)).get(order.getId()).stream().map(c -> c.id()).toList();
    }
    private Order order() {
        var request = new OrderCreateRequest();
        request.setPickupProvinceCode("79"); request.setPickupAreaCode("SGN-THUDUC");
        request.setDeliveryProvinceCode("79"); request.setDeliveryAreaCode("SGN-Q1");
        request.setSenderName("Người gửi kiểm thử"); request.setSenderAddress("Số 1 Võ Văn Ngân");
        request.setReceiverName("Người nhận kiểm thử"); request.setReceiverPhone("0901234567"); request.setReceiverAddress("Số 2 Lê Lợi");
        request.setWeightKg(1.0); request.setServiceType(Order.ServiceType.STANDARD); request.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        return orderService.createOrder(request, customer.getUsername());
    }
    /** Cho shipper giữ sẵn n đơn đang chạy (đã phân công, chờ lấy). */
    private void hold(AppUser shipper, int count) {
        for (int i = 0; i < count; i++) {
            var order = order(); order.setShipper(shipper); orders.saveAndFlush(order);
        }
    }
    private ProvinceArea area(String code) { return areas.findByAreaCode(code).orElseThrow(); }
    private void branch(AppUser user, String code) { staffBranches.save(new StaffBranch(user, branches.findByCode(code).orElseThrow(), admin, NOW)); }
    private AppUser shipper(String username, String branchCode, String primaryArea, String secondaryArea) {
        var user = account(username, AppUser.Role.SHIPPER); branch(user, branchCode);
        zones.save(new ShipperZone(user, area(primaryArea), true));
        if (secondaryArea != null) zones.save(new ShipperZone(user, area(secondaryArea), false));
        return user;
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
