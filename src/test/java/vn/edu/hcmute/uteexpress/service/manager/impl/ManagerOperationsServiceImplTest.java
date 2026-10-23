package vn.edu.hcmute.uteexpress.service.manager.impl;

import jakarta.validation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.manager.*;
import vn.edu.hcmute.uteexpress.service.admin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManagerOperationsServiceImplTest {
    private final ManagerDispatchRepository orders = mock(ManagerDispatchRepository.class);
    private final AppUserRepository shippers = mock(AppUserRepository.class);
    private final OrderRepository sharedOrders = mock(OrderRepository.class);
    private final PromoCodeRepository promotions = mock(PromoCodeRepository.class);
    private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private ValidatorFactory factory;
    private ManagerDispatchServiceImpl dispatch;
    private ManagerPromotionServiceImpl promoService;
    private ManagerReportServiceImpl reports;
    private Order order;
    private AppUser shipper;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        var staff = new StaffAccount(); var user = new AppUser("manager01", "hash", "test@example.com");
        user.setRole(AppUser.Role.MANAGER); user.setEnabled(true); staff.setUser(user);
        staff.setPasswordChangeRequired(false);
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.of(staff));
        var access = new StaffAccessPolicy(accounts, factory.getValidator());
        var routeRepository = mock(vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository.class);
        var staffBranches = mock(vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository.class);
        var policyService = mock(OperationsPolicyService.class);
        when(policyService.current()).thenReturn(new vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy());
        var routing = new vn.edu.hcmute.uteexpress.service.routing.DispatchRoutingPolicy(staffBranches,
                mock(vn.edu.hcmute.uteexpress.repository.routing.ShipperZoneRepository.class),
                mock(vn.edu.hcmute.uteexpress.repository.routing.AreaNeighborRepository.class), routeRepository, sharedOrders,
                mock(vn.edu.hcmute.uteexpress.repository.routing.BranchRepository.class), policyService,
                mock(vn.edu.hcmute.uteexpress.repository.admin.AuditEventRepository.class), clock);
        var notificationRepository = mock(ShipperNotificationRepository.class);
        when(notificationRepository.findByOrderAndRecipient(any(), any())).thenReturn(Optional.empty());
        var shared = new vn.edu.hcmute.uteexpress.service.tracking.DispatchService(sharedOrders, shippers,
                mock(OrderStatusHistoryRepository.class), notificationRepository,
                new vn.edu.hcmute.uteexpress.service.tracking.LogisticsAccess(shippers, accounts, staffBranches, routeRepository), clock,
                mock(org.springframework.context.ApplicationEventPublisher.class), audit,
                mock(vn.edu.hcmute.uteexpress.service.OrderPaymentService.class), routing, access);
        dispatch = new ManagerDispatchServiceImpl(shared);
        user.setId(2L);
        var branch = new vn.edu.hcmute.uteexpress.entity.routing.Branch("SGN", "Kho SGN", "79", "Thủ Đức", "SGN-THUDUC", LocalDateTime.now(clock));
        org.springframework.test.util.ReflectionTestUtils.setField(branch, "id", 1L);
        when(staffBranches.findByUser_Id(anyLong())).thenAnswer(inv -> Optional.of(new vn.edu.hcmute.uteexpress.entity.routing.StaffBranch(user, branch, user, LocalDateTime.now(clock))));
        var area = new vn.edu.hcmute.uteexpress.entity.routing.ProvinceArea("79", "SGN-THUDUC", "Thủ Đức", branch, java.math.BigDecimal.TEN, java.math.BigDecimal.TEN);
        when(routeRepository.findByOrder_Id(1L)).thenAnswer(inv -> Optional.of(new vn.edu.hcmute.uteexpress.entity.routing.OrderRoute(order, area, area)));
        promoService = new ManagerPromotionServiceImpl(promotions, access, audit, clock);
        reports = new ManagerReportServiceImpl(orders, access, clock, org.mockito.Mockito.mock(vn.edu.hcmute.uteexpress.service.routing.BranchScope.class, invocation -> null));
        order = new Order(); order.setId(1L); order.setStatus(Order.OrderStatus.PENDING_PICKUP);
        shipper = new AppUser("shipper", "hash", "shipper@example.com"); shipper.setId(3L);
        shipper.setRole(AppUser.Role.SHIPPER); shipper.setEnabled(true);
    }
    @AfterEach void close() { factory.close(); }
    private OrderAssignmentRequest assignment() {
        var request = new OrderAssignmentRequest(); request.setShipperId(3L); request.setRevision(0L);
        request.setReason("Điều phối tải đơn"); return request;
    }
    @Test void assigningPreservesStatusAndRecordsAudit() {
        when(sharedOrders.findLockedById(1L)).thenReturn(Optional.of(order));
        when(shippers.lockById(3L)).thenReturn(Optional.of(shipper));
        dispatch.assignOrder("manager01", 1L, assignment());
        assertSame(shipper, order.getShipper()); assertEquals(Order.OrderStatus.PENDING_PICKUP, order.getStatus());
        assertEquals(LocalDateTime.now(clock), order.getUpdatedAt());
        verify(sharedOrders).save(order); verify(audit).record(eq("manager01"), eq("ORDER_ASSIGN"), eq("order:1"), anyString());
    }
    @Test void staleRevisionNeverAssigns() {
        when(sharedOrders.findLockedById(1L)).thenReturn(Optional.of(order));
        when(shippers.lockById(3L)).thenReturn(Optional.of(shipper));
        var form = assignment(); form.setRevision(1L);
        assertThrows(StaffConflictException.class, () -> dispatch.assignOrder("manager01", 1L, form));
        verifyNoInteractions(audit); verify(sharedOrders, never()).save(any());
    }
    @Test void everyNonPendingStatusRejectsAssignment() {
        when(sharedOrders.findLockedById(1L)).thenReturn(Optional.of(order));
        when(shippers.lockById(3L)).thenReturn(Optional.of(shipper));
        for (var status : Order.OrderStatus.values()) {
            if (status == Order.OrderStatus.PENDING_PICKUP) { continue; }
            order.setStatus(status);
            assertThrows(IllegalArgumentException.class, () -> dispatch.assignOrder("manager01", 1L, assignment()));
        }
        verifyNoInteractions(audit);
    }
    @Test void disabledOrWrongRoleAndSameShipperReject() {
        when(sharedOrders.findLockedById(1L)).thenReturn(Optional.of(order));
        when(shippers.lockById(3L)).thenReturn(Optional.of(shipper));
        shipper.setEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> dispatch.assignOrder("manager01", 1L, assignment()));
        shipper.setEnabled(true); shipper.setRole(AppUser.Role.ADMIN);
        assertThrows(IllegalArgumentException.class, () -> dispatch.assignOrder("manager01", 1L, assignment()));
        shipper.setRole(AppUser.Role.SHIPPER); order.setShipper(shipper);
        assertThrows(IllegalArgumentException.class, () -> dispatch.assignOrder("manager01", 1L, assignment()));
        verifyNoInteractions(audit);
    }
    @Test void adminLockedShipperIsRejectedEvenIfEnabledIsTrue() {
        when(sharedOrders.findLockedById(1L)).thenReturn(Optional.of(order));
        when(shippers.lockById(3L)).thenReturn(Optional.of(shipper));
        shipper.setLockedByAdmin(true);
        assertThrows(IllegalArgumentException.class, () -> dispatch.assignOrder("manager01", 1L, assignment()));
        verifyNoInteractions(audit);
    }
    @Test void invalidIdReasonAndMissingShipperReject() {
        var form = assignment();
        assertThrows(IllegalArgumentException.class, () -> dispatch.assignOrder("manager01", -1L, form));
        form.setReason("x");
        assertThrows(IllegalArgumentException.class, () -> dispatch.assignOrder("manager01", 1L, form));
        form.setReason("abc\ndef");
        assertThrows(IllegalArgumentException.class, () -> dispatch.assignOrder("manager01", 1L, form));
        verifyNoInteractions(sharedOrders, shippers, audit);
    }
    @Test void revokedOrUnknownOperatorCannotWrite() {
        assertThrows(AccessDeniedException.class, () -> dispatch.assignOrder("unknown", 1L, assignment()));
        var account = accounts.findByUserUsername("manager01").orElseThrow();
        account.setActive(false);
        assertThrows(AccessDeniedException.class, () -> dispatch.assignOrder("manager01", 1L, assignment()));
        verifyNoInteractions(sharedOrders, shippers, audit);
    }
    private PromotionRequest promotion() {
        var form = new PromotionRequest(); form.setCode(" demo10 "); form.setDescription("Giảm cước demo");
        form.setDiscountType(PromoCode.DiscountType.PERCENT); form.setDiscountValue(BigDecimal.TEN);
        form.setMaxDiscountAmount(BigDecimal.valueOf(30000)); form.setMinOrderAmount(BigDecimal.ZERO);
        form.setUsageLimit(100); form.setStartAt(LocalDateTime.now(clock).minusDays(1));
        form.setEndAt(LocalDateTime.now(clock).plusDays(30)); return form;
    }
    private PromoCode stored(PromotionRequest form) {
        var promo = new PromoCode(); promo.setId(7L); promo.setCode(form.getCode()); promo.setDescription(form.getDescription());
        promo.setDiscountType(form.getDiscountType()); promo.setDiscountValue(form.getDiscountValue());
        promo.setMaxDiscountAmount(form.getMaxDiscountAmount()); promo.setMinOrderAmount(form.getMinOrderAmount());
        promo.setStartAt(form.getStartAt()); promo.setEndAt(form.getEndAt()); promo.setUsageLimit(form.getUsageLimit());
        when(promotions.lockById(7L)).thenReturn(Optional.of(promo)); return promo;
    }
    @Test void createNormalizesCodeAndAudits() {
        var form = promotion(); promoService.savePromotion("manager01", null, form);
        assertEquals("DEMO10", form.getCode());
        verify(promotions).saveAndFlush(any(PromoCode.class)); verify(audit).record(eq("manager01"), eq("PROMOTION_CREATE"), anyString(), anyString());
    }
    @Test void invalidDiscountPeriodAndUsageFailBeforeDbWrite() {
        var invalidPercent = promotion(); invalidPercent.setDiscountValue(BigDecimal.valueOf(101));
        assertThrows(IllegalArgumentException.class, () -> promoService.savePromotion("manager01", null, invalidPercent));
        var noCap = promotion(); noCap.setMaxDiscountAmount(null);
        assertThrows(IllegalArgumentException.class, () -> promoService.savePromotion("manager01", null, noCap));
    }
    @Test void duplicateAndExpiredCreateReject() {
        var form = promotion(); when(promotions.findByCodeIgnoreCase("DEMO10")).thenReturn(Optional.of(new PromoCode()));
        assertThrows(IllegalArgumentException.class, () -> promoService.savePromotion("manager01", null, form));
        var expired = promotion(); expired.setStartAt(LocalDateTime.now(clock).minusDays(10));
        expired.setEndAt(LocalDateTime.now(clock).minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> promoService.savePromotion("manager01", null, expired));
        verify(promotions, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }
    @Test void usedPromotionLocksFinancialTermsButAllowsDisable() {
        var form = promotion(); var promo = stored(form); promo.setUsedCount(10);
        form.setDiscountValue(BigDecimal.valueOf(20));
        assertThrows(IllegalArgumentException.class, () -> promoService.savePromotion("manager01", 7L, form));
        form.setDiscountValue(BigDecimal.TEN); form.setUsageLimit(9);
        assertThrows(IllegalArgumentException.class, () -> promoService.savePromotion("manager01", 7L, form));
        form.setUsageLimit(10); form.setActive(false);
        promoService.savePromotion("manager01", 7L, form);
        assertFalse(promo.isActive()); assertEquals(10, promo.getUsedCount());
    }
    @Test void cannotRenameOrSubmitOldPromoRevision() {
        var form = promotion(); stored(form); form.setCode("NEWCODE");
        assertThrows(IllegalArgumentException.class, () -> promoService.savePromotion("manager01", 7L, form));
        form.setCode("DEMO10"); form.setRevision(2L);
        assertThrows(StaffConflictException.class, () -> promoService.savePromotion("manager01", 7L, form));
    }
    @Test void invalidReportRangeAndPagesRejectBeforeQueries() {
        var today = LocalDate.now(clock);
        for (var range : List.of(new LocalDate[]{today, today.minusDays(1)},
                new LocalDate[]{today.minusDays(366), today}, new LocalDate[]{today, today.plusDays(1)})) {
            assertThrows(IllegalArgumentException.class, () -> reports.report("manager01", range[0], range[1]));
        }
        assertThrows(IllegalArgumentException.class, () -> dispatch.findPendingOrders("manager01", -1));
        assertThrows(IllegalArgumentException.class, () -> dispatch.findShippers("manager01", 10001));
        verifyNoInteractions(orders);
    }
    @Test void emptyReportIsZeroNotNaN() {
        when(orders.countStatuses(any(), any(), any())).thenReturn(List.of());
        var report = reports.report("manager01", LocalDate.now(clock).minusDays(2), LocalDate.now(clock));
        assertEquals(0, report.totalOrders()); assertEquals(new BigDecimal("0.0"), report.completionRate());
        assertEquals(BigDecimal.ZERO, report.paidShippingFees()); assertEquals(7, report.counts().size());
    }
    @Test void failedDeliveriesStillAwaitingRetryAreNotFinalOutcomes() {
        var delivered = mock(ManagerDispatchRepository.StatusCount.class);
        when(delivered.getStatus()).thenReturn(Order.OrderStatus.DELIVERED); when(delivered.getTotal()).thenReturn(8L);
        var returned = mock(ManagerDispatchRepository.StatusCount.class);
        when(returned.getStatus()).thenReturn(Order.OrderStatus.RETURNED); when(returned.getTotal()).thenReturn(2L);
        var failed = mock(ManagerDispatchRepository.StatusCount.class);
        when(failed.getStatus()).thenReturn(Order.OrderStatus.FAILED); when(failed.getTotal()).thenReturn(10L);
        when(orders.countStatuses(any(), any(), any())).thenReturn(List.of(delivered, returned, failed));
        var report = reports.report("manager01", LocalDate.now(clock), LocalDate.now(clock));
        assertEquals(20L, report.totalOrders()); assertEquals(new BigDecimal("80.0"), report.completionRate());
    }
}


