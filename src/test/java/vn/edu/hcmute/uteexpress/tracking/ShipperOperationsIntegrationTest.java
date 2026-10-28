package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Lấy hàng thất bại, lộ trình, cập nhật hàng loạt, đối soát COD và validation; chạy trên H2 riêng. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:shipper-ops-test;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload.dir=target/test-proof-uploads", "app.mail.order-updates.enabled=false"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("isolated-logistics")
@Import(ShipperOperationsIntegrationTest.TestClock.class)
@RecordApplicationEvents
@Transactional
class ShipperOperationsIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired ApplicationEvents events;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired OrderPaymentRepository payments;
    @Autowired StaffAccountRepository staff;
    @Autowired CodSettlementRepository settlements;
    @Autowired CodSettlementItemRepository settlementItems;
    @Autowired TrackingService tracking;
    @Autowired ShipperWorkspaceService workspace;
    @Autowired ShipperRouteService routes;
    @Autowired ShipperBulkService bulk;
    @Autowired CodSettlementService cod;
    @Autowired ShipperStatisticsService statistics;
    AppUser customer, shipper, anotherShipper;
    Order order;
    Authentication managerAuth;

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        customer = account("customer-ops", AppUser.Role.USER);
        shipper = account("shipper-ops", AppUser.Role.SHIPPER);
        anotherShipper = account("other-shipper-ops", AppUser.Role.SHIPPER);
        var manager = account("manager-ops", AppUser.Role.MANAGER);
        var staffAccount = new StaffAccount(); staffAccount.setUser(manager); staffAccount.setActive(true);
        staffAccount.setPasswordChangeRequired(false); staffAccount.setIssuedBy("test-only");
        staff.saveAndFlush(staffAccount);
        var principal = new StaffPrincipal(staffAccount);
        managerAuth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
        order = order("UTE-OPS-01", shipper, Order.OrderStatus.PENDING_PICKUP);
        payment(order, OrderPayment.PaymentMethod.COD, OrderPayment.PaymentStatus.UNPAID, 18000);
    }

    /* ===================== Lấy hàng thất bại ===================== */

    @Test void detailKeepsAnEvidenceRegionBeforeDeliveryAndRendersTheFormAndHistoryWhenTransitStarts() throws Exception {
        order.setStatus(Order.OrderStatus.PICKED_UP); orders.flush();
        String path = "/shipper/don/" + order.getId();
        mvc.perform(get(path).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-realtime-region=\"shipper-delivery-evidence\"")))
                .andExpect(content().string(containsString("data-realtime-region=\"shipper-history\"")))
                .andExpect(content().string(not(containsString("id=\"deliveryProofForm\""))));
        tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.IN_TRANSIT, null, null, null);
        mvc.perform(get(path).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"deliveryProofForm\"")))
                .andExpect(content().string(containsString("id=\"signatureCanvas\"")))
                .andExpect(content().string(containsString("Người cập nhật")));
    }

    @Test void pickupFailureKeepsOrderWaitingRecordsReasonAndNotifiesSender() throws Exception {
        var entry = tracking.reportPickupFailure(order.getId(), shipper.getUsername(), DeliveryFailureReason.CUSTOMER_ABSENT,
                "  Người gửi hẹn chiều nay  ", NOW.plusHours(6));
        assertEquals(Order.OrderStatus.PENDING_PICKUP, order.getStatus());
        assertTrue(entry.isPickupFailure());
        assertEquals("Người gửi không có mặt", entry.getReasonLabel());
        assertEquals("Người gửi hẹn chiều nay", entry.getNote());
        assertEquals(1, tracking.pickupFailures(order));
        var sent = events.stream(SenderOrderUpdateEvent.class).toList();
        assertEquals(1, sent.size());
        assertEquals(SenderOrderUpdateEvent.Kind.PICKUP_FAILED, sent.get(0).kind());
        assertEquals("customer-ops@test.invalid", sent.get(0).email());
        assertEquals("Người gửi không có mặt", sent.get(0).reason());
        // Không tính là một lượt giao trong thống kê (trạng thái không đổi).
        assertEquals(0, statistics.getStatistics(shipper.getUsername(), ShipperStatisticsPeriod.DAY, null, null).totalOrders());
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/hanh-trinh").with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Lấy hàng không thành công")))
            .andExpect(content().string(containsString("Hẹn lấy hàng lại: 11/10/2026 15:00")));
        mvc.perform(get("/shipper/don/" + order.getId()).with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Lấy hàng chưa thành công (1/3 lần)")));
    }

    @Test void pickupFailureValidatesEveryInput() {
        String u = shipper.getUsername();
        Long id = order.getId();
        assertThrows(IllegalArgumentException.class, () -> tracking.reportPickupFailure(id, u, null, "", null));
        assertThrows(IllegalArgumentException.class, () -> tracking.reportPickupFailure(id, u, DeliveryFailureReason.OTHER, "abc", null));
        assertThrows(IllegalArgumentException.class, () -> tracking.reportPickupFailure(id, u, DeliveryFailureReason.CUSTOMER_ABSENT, "", NOW));
        assertThrows(IllegalArgumentException.class, () -> tracking.reportPickupFailure(id, u, DeliveryFailureReason.CUSTOMER_ABSENT, "", NOW.plusDays(31)));
        assertThrows(IllegalArgumentException.class, () -> tracking.reportPickupFailure(id, u, DeliveryFailureReason.CUSTOMER_ABSENT, "x".repeat(501), null));
        assertThrows(IllegalArgumentException.class, () -> tracking.reportPickupFailure(id, u, DeliveryFailureReason.CUSTOMER_ABSENT, "a\u0000b", null));
        assertThrows(IllegalStateException.class, () -> tracking.reportPickupFailure(id, anotherShipper.getUsername(), DeliveryFailureReason.CUSTOMER_ABSENT, "", null));
        assertThrows(IllegalStateException.class, () -> tracking.reportPickupFailure(id, customer.getUsername(), DeliveryFailureReason.CUSTOMER_ABSENT, "", null));
        var picked = order("UTE-OPS-PICKED", shipper, Order.OrderStatus.PICKED_UP);
        assertThrows(IllegalStateException.class, () -> tracking.reportPickupFailure(picked.getId(), u, DeliveryFailureReason.CUSTOMER_ABSENT, "", null));
        assertEquals(0, history.count());
        tracking.reportPickupFailure(id, u, DeliveryFailureReason.OTHER, "Cổng khóa, không ai mở", null);
        assertThrows(IllegalStateException.class, () -> tracking.reportPickupFailure(id, u, DeliveryFailureReason.CUSTOMER_ABSENT, "", null),
                "ghi nhận lặp trong 1 phút phải bị chặn");
    }

    @Test void pickupFailureStopsAfterThreeAttempts() {
        for (int i = 3; i > 0; i--) pickupFailure(order, NOW.minusHours(i), null);
        assertFalse(tracking.canReportPickupFailure(order));
        var ex = assertThrows(IllegalStateException.class, () -> tracking.reportPickupFailure(order.getId(), shipper.getUsername(),
                DeliveryFailureReason.CUSTOMER_ABSENT, "", null));
        assertTrue(ex.getMessage().contains("3 lần"));
    }

    @Test void pickupEndpointNeedsCsrfAndKeepsInputOnError() throws Exception {
        String path = "/shipper/don/" + order.getId() + "/lay-hang-that-bai";
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).param("failureReason", "CUSTOMER_ABSENT"))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf()).param("failureReason", "CUSTOMER_ABSENT"))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf())
                .param("failureReason", "OTHER").param("note", "ngắn"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shipper/don/" + order.getId()))
            .andExpect(flash().attribute("error", "Lý do khác cần ghi chú ít nhất 5 ký tự."))
            .andExpect(flash().attribute("openForm", "pickup-failure")).andExpect(flash().attribute("formNote", "ngắn"));
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf())
                .param("failureReason", "NOT_A_REASON")).andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Dữ liệu gửi lên không hợp lệ")));
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf())
                .param("failureReason", "WRONG_ADDRESS").param("nextAttemptAt", "2026-10-12T08:30"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        assertEquals(1, tracking.pickupFailures(order));
    }

    @Test void workViewsHonourPickupAppointments() {
        var later = order("UTE-OPS-LATER", shipper, Order.OrderStatus.PENDING_PICKUP);
        pickupFailure(later, NOW.minusHours(1), NOW.toLocalDate().plusDays(1).atTime(8, 0));
        var due = order("UTE-OPS-DUE", shipper, Order.OrderStatus.PENDING_PICKUP);
        pickupFailure(due, NOW.minusHours(2), NOW.minusMinutes(5));
        var exhausted = order("UTE-OPS-EXHAUSTED", shipper, Order.OrderStatus.PENDING_PICKUP);
        for (int i = 3; i > 0; i--) pickupFailure(exhausted, NOW.minusHours(i), NOW.minusMinutes(1));
        var today = workspace.work(shipper.getUsername(), ShipperWorkView.TODAY, "", null, null, null, 0).map(Order::getTrackingCode).getContent();
        assertFalse(today.contains("UTE-OPS-LATER"), "lấy hàng đã hẹn sang ngày mai không nằm trong hôm nay");
        assertEquals("UTE-OPS-DUE", today.get(0), "đơn đến hẹn lấy lại được ưu tiên đầu tiên");
        assertTrue(today.contains("UTE-OPS-01"));
        var dueList = workspace.work(shipper.getUsername(), ShipperWorkView.RETRY_DUE, "", null, null, null, 0).map(Order::getTrackingCode).getContent();
        assertEquals(List.of("UTE-OPS-DUE"), dueList);
        assertEquals(1L, workspace.counts(shipper.getUsername()).get("retryDue"));
    }

    /* ===================== Lộ trình ===================== */

    @Test void routeGroupsStopsByAddressAndUsesPickupOrDeliveryAddress() throws Exception {
        var transit = order("UTE-OPS-TRANSIT", shipper, Order.OrderStatus.IN_TRANSIT);
        transit.setReceiverAddress("1 Võ Văn Ngân, Thủ Đức"); transit.setServiceType(Order.ServiceType.SUPER_EXPRESS);
        var sameAddress = order("UTE-OPS-SAME", shipper, Order.OrderStatus.PICKED_UP);
        sameAddress.setReceiverAddress("1  võ văn ngân,  Thủ Đức");
        var missing = order("UTE-OPS-NOADDR", shipper, Order.OrderStatus.PICKED_UP); missing.setReceiverAddress("  ");
        order("UTE-OPS-FOREIGN", anotherShipper, Order.OrderStatus.IN_TRANSIT);
        order("UTE-OPS-DONE", shipper, Order.OrderStatus.DELIVERED);
        orders.flush();
        var route = routes.today(shipper.getUsername());
        assertEquals(2, route.stops().size());
        assertEquals(3, route.orderCount());
        assertEquals(1, route.missingAddress());
        var first = route.stops().get(0);
        assertEquals("1 Võ Văn Ngân, Thủ Đức", first.address());
        assertEquals(List.of("UTE-OPS-TRANSIT", "UTE-OPS-SAME"), first.tasks().stream().map(ShipperRoute.Task::trackingCode).toList());
        assertTrue(route.stops().get(1).hasPickup());
        assertEquals("Địa chỉ lấy hàng", route.stops().get(1).address());
        assertEquals(1, route.legs().size());
        String url = route.legs().get(0).url();
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination="));
        assertTrue(url.contains("&waypoints=1+V%C3%B5+V%C4%83n+Ng%C3%A2n"));
        mvc.perform(get("/shipper/lo-trinh").with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("UTE-OPS-SAME")))
            .andExpect(content().string(not(containsString("UTE-OPS-FOREIGN"))))
            .andExpect(content().string(containsString("1 đơn thiếu địa chỉ")));
    }

    @Test void longRoutesAreSplitIntoLegsWithinGoogleLimits() {
        List<ShipperRoute.Stop> stops = new ArrayList<>();
        for (int i = 1; i <= 23; i++)
            stops.add(new ShipperRoute.Stop(i, "Số " + i + " đường Lê Văn Việt, phường Hiệp Phú, TP. Thủ Đức", List.of()));
        var legs = vn.edu.hcmute.uteexpress.service.tracking.ShipperRouteServiceTestAccess.legs(stops);
        assertTrue(legs.size() >= 3);
        assertEquals(1, legs.get(0).fromStop());
        assertEquals(23, legs.get(legs.size() - 1).toStop());
        for (int i = 0; i < legs.size(); i++) {
            var leg = legs.get(i);
            assertTrue(leg.url().length() <= 2000, "URL Google Maps phải ngắn hơn 2.000 ký tự");
            assertTrue(leg.toStop() - leg.fromStop() + 1 <= 10, "tối đa 10 điểm mỗi chặng");
            if (i > 0) {
                assertEquals(legs.get(i - 1).toStop() + 1, leg.fromStop(), "các chặng nối liền nhau");
                assertTrue(leg.url().contains("&origin="), "chặng sau xuất phát từ điểm cuối chặng trước");
            }
        }
        assertEquals("Phố cổ Hà Nội", vn.edu.hcmute.uteexpress.service.tracking.ShipperRouteServiceTestAccess.clean(" Phố |cổ\n Hà   Nội "));
        // Điện thoại: Google Maps trên trình duyệt chỉ nhận 3 điểm dừng giữa đường -> mỗi chặng tối đa 4 điểm, nối liền nhau.
        var mobile = vn.edu.hcmute.uteexpress.service.tracking.ShipperRouteServiceTestAccess.mobileLegs(stops);
        assertEquals(6, mobile.size(), "23 điểm chia 4 điểm mỗi chặng");
        assertEquals(23, mobile.get(mobile.size() - 1).toStop());
        for (int i = 0; i < mobile.size(); i++) {
            var leg = mobile.get(i);
            assertTrue(leg.toStop() - leg.fromStop() + 1 <= 4, "tối đa 4 điểm mỗi chặng trên điện thoại");
            assertTrue(leg.url().split("%7C", -1).length <= 3, "tối đa 3 điểm dừng giữa đường");
            if (i > 0) assertEquals(mobile.get(i - 1).toStop() + 1, leg.fromStop());
        }
    }

    /* ===================== Cập nhật hàng loạt ===================== */

    @Test void bulkUpdateChangesOnlyEligibleOwnOrdersAndReportsTheRest() {
        var second = order("UTE-OPS-02", shipper, Order.OrderStatus.PENDING_PICKUP);
        payment(second, OrderPayment.PaymentMethod.COD, OrderPayment.PaymentStatus.UNPAID, 18000);
        var picked = order("UTE-OPS-03", shipper, Order.OrderStatus.PICKED_UP);
        var foreign = order("UTE-OPS-FOREIGN", anotherShipper, Order.OrderStatus.PENDING_PICKUP);
        var result = bulk.update(shipper.getUsername(), Arrays.asList(order.getId(), second.getId(), second.getId(),
                picked.getId(), foreign.getId()), Order.OrderStatus.PICKED_UP);
        assertEquals(List.of("UTE-OPS-01", "UTE-OPS-02"), result.updated());
        assertEquals(2, result.failed().size());
        assertEquals("UTE-OPS-03", result.failed().get(0).label());
        assertTrue(result.failed().get(0).reason().contains("Đã lấy hàng sang Đã lấy hàng"));
        assertEquals("Đơn #" + foreign.getId(), result.failed().get(1).label(), "không lộ mã đơn của Shipper khác");
        assertTrue(result.summary().startsWith("Đã cập nhật 2/4 đơn."));
        assertEquals(Order.OrderStatus.PICKED_UP, orders.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(Order.OrderStatus.PENDING_PICKUP, orders.findById(foreign.getId()).orElseThrow().getStatus());
    }

    @Test void bulkUpdateRejectsUnsafeRequests() throws Exception {
        String u = shipper.getUsername();
        assertThrows(IllegalArgumentException.class, () -> bulk.update(u, List.of(order.getId()), Order.OrderStatus.DELIVERED));
        assertThrows(IllegalArgumentException.class, () -> bulk.update(u, List.of(), Order.OrderStatus.PICKED_UP));
        assertThrows(IllegalArgumentException.class, () -> bulk.update(u, null, Order.OrderStatus.PICKED_UP));
        assertThrows(IllegalArgumentException.class, () -> bulk.update(u, List.of(-1L), Order.OrderStatus.PICKED_UP));
        var tooMany = new ArrayList<Long>();
        for (long i = 1; i <= 51; i++) tooMany.add(i);
        assertThrows(IllegalArgumentException.class, () -> bulk.update(u, tooMany, Order.OrderStatus.PICKED_UP));
        String path = "/shipper/cap-nhat-nhieu";
        mvc.perform(post(path).servletPath(path).with(user(u).roles("SHIPPER")).param("orderIds", String.valueOf(order.getId()))
                .param("status", "PICKED_UP")).andExpect(status().isForbidden());
        mvc.perform(post(path).servletPath(path).with(user(u).roles("SHIPPER")).with(csrf())
                .param("orderIds", String.valueOf(order.getId())).param("status", "PICKED_UP")
                .param("work", "TODAY").param("filterStatus", "PENDING_PICKUP").param("keyword", "Mai & co").param("page", "2"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/shipper/trang-chu?work=TODAY&status=PENDING_PICKUP&keyword=Mai%20%26%20co&page=2"))
            .andExpect(flash().attribute("message", "Đã cập nhật 1/1 đơn."));
        mvc.perform(post(path).servletPath(path).with(user(u).roles("SHIPPER")).with(csrf()).param("status", "PICKED_UP"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shipper/trang-chu"))
            .andExpect(flash().attribute("error", "Hãy chọn ít nhất một đơn."));
    }

    /* ===================== Đối soát COD ===================== */

    @Test void unsettledCodIncludesOnlyCashThisShipperCollected() {
        var mine = deliveredCod("UTE-COD-MINE", shipper, 25000);
        deliveredCod("UTE-COD-OTHER", anotherShipper, 30000);
        var legacy = order("UTE-COD-LEGACY", shipper, Order.OrderStatus.DELIVERED);
        payment(legacy, OrderPayment.PaymentMethod.COD, OrderPayment.PaymentStatus.PAID, 40000);
        var prepaid = order("UTE-COD-MOMO", shipper, Order.OrderStatus.DELIVERED);
        payment(prepaid, OrderPayment.PaymentMethod.MOMO, OrderPayment.PaymentStatus.PAID, 50000);
        delivered(prepaid, shipper);
        var held = cod.held(shipper.getUsername());
        assertEquals(1, held.count());
        assertEquals(new BigDecimal("25000"), held.amount());
        assertEquals(List.of(mine.getId()), cod.unsettled(shipper.getUsername()).stream().map(p -> p.getOrder().getId()).toList());
    }

    @Test void codSubmissionValidatesAmountsAndBlocksDoubleSettlement() {
        var a = deliveredCod("UTE-COD-A", shipper, 20000);
        var b = deliveredCod("UTE-COD-B", shipper, 15000);
        String u = shipper.getUsername();
        assertThrows(IllegalArgumentException.class, () -> cod.submit(u, List.of(), new BigDecimal("1"), ""));
        assertThrows(IllegalArgumentException.class, () -> cod.submit(u, List.of(a.getId(), order.getId()), new BigDecimal("20000"), ""),
                "đơn chưa giao/không phải COD không được nộp");
        assertThrows(IllegalArgumentException.class, () -> cod.submit(u, List.of(a.getId(), b.getId()), new BigDecimal("35001"), ""));
        assertThrows(IllegalArgumentException.class, () -> cod.submit(u, List.of(a.getId(), b.getId()), BigDecimal.ZERO, ""));
        assertThrows(IllegalArgumentException.class, () -> cod.submit(u, List.of(a.getId(), b.getId()), new BigDecimal("30000"), "thiếu"),
                "nộp thiếu cần ghi chú ít nhất 10 ký tự");
        assertThrows(IllegalArgumentException.class, () -> cod.submit(anotherShipper.getUsername(), List.of(a.getId()), new BigDecimal("20000"), ""));
        var settlement = cod.submit(u, List.of(a.getId(), b.getId(), a.getId()), new BigDecimal("35000"), "");
        assertEquals(2, settlement.getOrderCount());
        assertEquals(new BigDecimal("35000"), settlement.getTotalAmount());
        assertEquals(CodSettlement.Status.SUBMITTED, settlement.getStatus());
        assertEquals(0, cod.held(u).count());
        assertEquals(new BigDecimal("35000"), cod.held(u).pendingAmount());
        var c = deliveredCod("UTE-COD-C", shipper, 10000);
        var pendingError = assertThrows(IllegalArgumentException.class, () -> cod.submit(u, List.of(c.getId()), new BigDecimal("10000"), ""));
        assertTrue(pendingError.getMessage().contains("chờ Manager xác nhận"));
    }

    @Test void parseAmountAcceptsVietnameseGroupingOnly() {
        assertEquals(new BigDecimal("35000"), CodSettlementService.parseAmount(" 35.000 "));
        assertEquals(new BigDecimal("35000"), CodSettlementService.parseAmount("35,000đ"));
        for (String bad : List.of("", "  ", "abc", "-5", "1e5", "35.000,5x", "1234567890123", "1.2", "1,5", "1.000,000", "12 34"))
            assertThrows(IllegalArgumentException.class, () -> CodSettlementService.parseAmount(bad), bad);
    }

    @Test void managerConfirmsOrRejectsWithRevisionCheck() {
        var a = deliveredCod("UTE-COD-R1", shipper, 20000);
        var first = cod.submit(shipper.getUsername(), List.of(a.getId()), new BigDecimal("15000"), "Khách trả thiếu 5.000 đồng");
        long revision = first.getRevision();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> cod.review(shipper.getUsername(), first.getId(), revision, true, ""));
        assertThrows(IllegalArgumentException.class, () -> cod.review("manager-ops", first.getId(), revision + 1, true, "Đủ"));
        assertThrows(IllegalArgumentException.class, () -> cod.review("manager-ops", first.getId(), revision, true, ""),
                "phiếu chênh lệch cần ghi chú khi xác nhận");
        assertThrows(IllegalArgumentException.class, () -> cod.review("manager-ops", first.getId(), revision, false, "no"));
        cod.review("manager-ops", first.getId(), revision, false, "Thiếu tiền, nộp lại đủ");
        assertEquals(CodSettlement.Status.REJECTED, settlements.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(0, settlementItems.count(), "từ chối trả đơn về danh sách chờ nộp");
        assertEquals(1, cod.held(shipper.getUsername()).count());
        var second = cod.submit(shipper.getUsername(), List.of(a.getId()), new BigDecimal("20000"), "");
        cod.review("manager-ops", second.getId(), second.getRevision(), true, "");
        var confirmed = settlements.findById(second.getId()).orElseThrow();
        assertEquals(CodSettlement.Status.CONFIRMED, confirmed.getStatus());
        assertNotNull(confirmed.getReviewedAt());
        assertThrows(IllegalArgumentException.class, () -> cod.review("manager-ops", second.getId(), confirmed.getRevision(), false, "Đổi ý"),
                "phiếu đã xử lý không sửa được");
    }

    @Test void codPagesEnforceOwnershipCsrfAndShowAmounts() throws Exception {
        var a = deliveredCod("UTE-COD-W1", shipper, 18000);
        mvc.perform(get("/shipper/doi-soat").with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("18.000 đ")))
            .andExpect(content().string(containsString("UTE-COD-W1")));
        String path = "/shipper/doi-soat";
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER"))
                .param("orderIds", String.valueOf(a.getId())).param("declaredAmount", "18000")).andExpect(status().isForbidden());
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf())
                .param("orderIds", String.valueOf(a.getId())).param("declaredAmount", "mười tám nghìn"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shipper/doi-soat"))
            .andExpect(flash().attribute("error", "Số tiền là VND nguyên; dấu phân cách phải đúng nhóm 3 chữ số, không nhận số thập phân."))
            .andExpect(flash().attribute("formAmount", "mười tám nghìn"));
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf())
                .param("orderIds", String.valueOf(a.getId())).param("declaredAmount", "18.000"))
            .andExpect(status().is3xxRedirection());
        var settlement = settlements.findAll().get(0);
        mvc.perform(get("/shipper/doi-soat/" + settlement.getId()).with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Phiếu #" + settlement.getId())));
        mvc.perform(get("/shipper/doi-soat/" + settlement.getId()).with(user(anotherShipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Không tìm thấy phiếu nộp tiền.")));
        mvc.perform(get("/manager/doi-soat-cod").with(authentication(managerAuth)))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Phiếu chờ xác nhận")));
        mvc.perform(get("/manager/doi-soat-cod").with(user("fake-manager").roles("MANAGER"))).andExpect(status().isForbidden());
        String review = "/manager/doi-soat-cod/" + settlement.getId();
        mvc.perform(post(review).with(authentication(managerAuth)).with(csrf()).param("revision", String.valueOf(settlement.getRevision()))
                .param("decision", "MAYBE")).andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("error", "Hãy chọn xác nhận hoặc từ chối phiếu."));
        mvc.perform(post(review).with(authentication(managerAuth)).with(csrf()).param("revision", String.valueOf(settlement.getRevision()))
                .param("decision", "CONFIRM")).andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("message", "Đã xác nhận phiếu nộp tiền."));
        assertEquals(CodSettlement.Status.CONFIRMED, settlements.findById(settlement.getId()).orElseThrow().getStatus());
    }

    /* ===================== Validation chung ===================== */

    @Test void dashboardShowsFilterErrorsInsteadOfFailing() throws Exception {
        var shipperUser = user(shipper.getUsername()).roles("SHIPPER");
        mvc.perform(get("/shipper/trang-chu").param("keyword", "x".repeat(101)).with(shipperUser))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Bộ lọc chưa hợp lệ: Từ khóa phải có 0–100 ký tự hợp lệ.")));
        mvc.perform(get("/shipper/trang-chu").param("from", "2026-10-11").param("to", "2026-10-01").with(shipperUser))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Từ ngày không được sau đến ngày.")));
        mvc.perform(get("/shipper/trang-chu").param("page", "-1").with(shipperUser))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Số trang không hợp lệ.")));
        mvc.perform(get("/shipper/trang-chu").param("work", "HACK").with(shipperUser))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Về trang làm việc")))
            .andExpect(content().string(containsString("href=\"/shipper/trang-chu\"")));
        mvc.perform(get("/shipper/trang-chu").param("from", "11/10/2026").with(shipperUser)).andExpect(status().isBadRequest());
        mvc.perform(get("/shipper/don/abc").with(shipperUser)).andExpect(status().isBadRequest());
    }

    @Test void missingOrMalformedFormFieldsNeverCauseServerErrors() throws Exception {
        var shipperUser = user(shipper.getUsername()).roles("SHIPPER");
        String status = "/shipper/don/" + order.getId() + "/trang-thai";
        mvc.perform(post(status).servletPath(status).with(shipperUser).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post(status).servletPath(status).with(shipperUser).with(csrf()).param("status", "TELEPORTED")).andExpect(status().isBadRequest());
        mvc.perform(post(status).servletPath(status).with(shipperUser).with(csrf()).param("status", "DELIVERED"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", "Không thể chuyển trạng thái từ Chờ lấy hàng sang Giao thành công."));
        order.setStatus(Order.OrderStatus.IN_TRANSIT); orders.flush();
        mvc.perform(post(status).servletPath(status).with(shipperUser).with(csrf()).param("status", "FAILED")
                .param("failureReason", "CUSTOMER_ABSENT").param("nextAttemptAt", "2026-10-11T08:00").param("note", "Khách đi vắng"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("openForm", "delivery-failure"))
            .andExpect(flash().attribute("formNote", "Khách đi vắng"));
        String proof = "/shipper/don/" + order.getId() + "/bang-chung";
        mvc.perform(multipart(proof).servletPath(proof).with(shipperUser).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", "Vui lòng chọn ảnh bằng chứng giao hàng."));
        mvc.perform(multipart(proof).file(new MockMultipartFile("proofImage", "a.png", "image/png", new byte[]{1})).servletPath(proof)
                .with(shipperUser).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", "Vui lòng ký xác nhận trước khi lưu."));
        mvc.perform(multipart("/shipper/quet-ma").servletPath("/shipper/quet-ma").with(shipperUser).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", "Chọn ảnh JPG/PNG có dung lượng tối đa 5 MB."));
        mvc.perform(post("/shipper/quet-ma/ma").servletPath("/shipper/quet-ma/ma").with(shipperUser).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", "QR phải chứa mã vận đơn UTEExpress hợp lệ."));
    }

    @Test void unknownPagesAndWrongMethodsGetProperStatusInsteadOfSystemError() throws Exception {
        var shipperUser = user(shipper.getUsername()).roles("SHIPPER");
        mvc.perform(get("/shipper/trang-khong-ton-tai").with(shipperUser).accept("text/html"))
            .andExpect(status().isNotFound()).andExpect(view().name("error"))
            .andExpect(content().string(containsString("Không tìm thấy trang")))
            .andExpect(content().string(not(containsString("Loi he thong"))))
            .andExpect(content().string(not(containsString("No static resource"))));
        mvc.perform(get("/shipper/trang-khong-ton-tai").with(shipperUser).accept("application/json"))
            .andExpect(status().isNotFound())
            .andExpect(content().string("Không tìm thấy trang hoặc dữ liệu bạn yêu cầu."));
        mvc.perform(delete("/shipper/trang-chu").servletPath("/shipper/trang-chu").with(shipperUser).with(csrf()).accept("text/html"))
            .andExpect(status().isMethodNotAllowed()).andExpect(content().string(containsString("Thao tác không được hỗ trợ")));
        mvc.perform(get("/api/tracking/khong-co").accept("text/html")).andExpect(status().is4xxClientError())
            .andExpect(content().string(not(containsString("Loi he thong"))));
    }

    @Test void publicPagesOfferQrLookupDistanceQuotesAndChartsWithValidation() throws Exception {
        mvc.perform(get("/tra-cuu").accept("text/html")).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-qr-panel")))
            .andExpect(content().string(containsString("/js/public-qr-lookup.js")));
        mvc.perform(get("/uoc-tinh-cuoc").param("weightKg", "2.5")
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC").param("deliveryProvinceCode","79").param("deliveryAreaCode","SGN-THUDUC").accept("text/html"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Phụ phí khoảng cách")))
            .andExpect(content().string(containsString("22.500 đ")));
        mvc.perform(get("/uoc-tinh-cuoc").param("weightKg", "2").param("serviceType", "SUPER_EXPRESS")
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC").param("deliveryProvinceCode","01").param("deliveryAreaCode","HAN-CAUGIAY").accept("text/html"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Hỏa tốc chỉ nhận trong cùng chi nhánh và không quá 30 km")));
        mvc.perform(get("/uoc-tinh-cuoc").param("weightKg", "2")
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC").param("deliveryProvinceCode","99").param("deliveryAreaCode","OUTSIDE").accept("text/html"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("UTEExpress chưa phục vụ khu vực này")))
            .andExpect(content().string(not(containsString("Phụ phí khoảng cách</dt>"))));
        mvc.perform(get("/uoc-tinh-cuoc").param("weightKg", "2").param("serviceType", "TELEPORT").accept("text/html"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "serviceType"));
        mvc.perform(get("/shipper/thong-ke").param("period", "WEEK").with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("sx-donut")))
            .andExpect(content().string(containsString("data-stats-days")))
            .andExpect(content().string(containsString("Cước COD thu theo ngày")));
        // Khoảng ngày không có hoạt động: báo ngắn kèm khoảng ngày thay cho biểu đồ trống.
        mvc.perform(get("/shipper/thong-ke").param("from", "2001-01-01").param("to", "2001-01-07")
                .with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Chưa có dữ liệu")))
            .andExpect(content().string(containsString("Không có hoạt động giao nhận từ 01/01/2001 đến 07/01/2001")))
            .andExpect(content().string(not(containsString("sx-donut"))));
    }

    /* ===================== Dữ liệu thử ===================== */

    private Order deliveredCod(String code, AppUser assigned, int amount) {
        var value = order(code, assigned, Order.OrderStatus.DELIVERED);
        payment(value, OrderPayment.PaymentMethod.COD, OrderPayment.PaymentStatus.PAID, amount);
        delivered(value, assigned);
        return value;
    }
    private void delivered(Order value, AppUser actor) {
        history.saveAndFlush(new OrderStatusHistory(value, actor, Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.DELIVERED,
                null, "", null, NOW.minusHours(1)));
    }
    private void pickupFailure(Order value, LocalDateTime at, LocalDateTime next) {
        history.saveAndFlush(new OrderStatusHistory(value, shipper, Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.PENDING_PICKUP,
                DeliveryFailureReason.CUSTOMER_ABSENT, "Người gửi vắng", next, at));
    }
    private void payment(Order value, OrderPayment.PaymentMethod method, OrderPayment.PaymentStatus status, int amount) {
        var payment = payments.findByOrder(value).orElseGet(OrderPayment::new); payment.setOrder(value); payment.setMethod(method); payment.setStatus(status);
        payment.setAmount(BigDecimal.valueOf(amount)); payment.setPaidAt(status == OrderPayment.PaymentStatus.PAID ? NOW.minusHours(1) : null);
        payments.saveAndFlush(payment);
    }
    private AppUser account(String username, AppUser.Role role) {
        var user = new AppUser(username, "test-hash", username + "@test.invalid");
        user.setRole(role); user.setEnabled(true); user.setFullName(username);
        return users.saveAndFlush(user);
    }
    private Order order(String code, AppUser assigned, Order.OrderStatus status) {
        var result = new Order(); result.setTrackingCode(code); result.setSender(customer); result.setShipper(assigned);
        result.setSenderName("Người gửi thử nghiệm"); result.setReceiverName("Nguyễn Mai"); result.setReceiverPhone("0901234567");
        result.setSenderAddress("Địa chỉ lấy hàng"); result.setReceiverAddress("Địa chỉ giao hàng"); result.setWeightKg(1.0);
        result.setShippingFee(new BigDecimal("18000")); result.setStatus(status);
        result.setCreatedAt(NOW.minusDays(2)); result.setUpdatedAt(NOW.minusDays(2));
        return orders.saveAndFlush(result);
    }
}
