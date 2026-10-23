package vn.edu.hcmute.uteexpress.service.tracking.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofService;

@ExtendWith(MockitoExtension.class)
class TrackingServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderPaymentService orderPaymentService;

    @Mock
    private DeliveryProofService deliveryProofService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock private vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository history;
    @Mock private vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService operations;
    @Mock private vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes;
    @Spy private java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-11T02:00:00Z"),
            java.time.ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks
    private TrackingServiceImpl trackingService;

    @Test
    void findAssignedOrders_returnsNewestOrderFirst() {
        AppUser shipper = createShipper();

        Order olderOrder = new Order();
        olderOrder.setCreatedAt(LocalDateTime.of(2026, 9, 12, 8, 0));

        Order newerOrder = new Order();
        newerOrder.setCreatedAt(LocalDateTime.of(2026, 9, 13, 8, 0));

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findByShipper(shipper))
                .thenReturn(List.of(olderOrder, newerOrder));

        List<Order> result =
                trackingService.findAssignedOrders("nguyentai");

        assertEquals(List.of(newerOrder, olderOrder), result);
        verify(orderRepository).findByShipper(shipper);
    }

    @Test
    void findAssignedOrder_returnsOrderAssignedToCurrentShipper() {
        AppUser shipper = createShipper();
        Order assignedOrder = createOrder(
                shipper, Order.OrderStatus.PENDING_PICKUP);

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(assignedOrder));

        Optional<Order> result =
                trackingService.findAssignedOrder(1L, "nguyentai");

        assertTrue(result.isPresent());
        assertSame(assignedOrder, result.get());
        verify(orderRepository).findByIdAndShipper(1L, shipper);
    }

    @Test
    void findAssignedOrder_returnsEmptyForAnotherShippersOrder() {
        AppUser shipper = createShipper();

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdAndShipper(99L, shipper))
                .thenReturn(Optional.empty());

        Optional<Order> result =
                trackingService.findAssignedOrder(99L, "nguyentai");

        assertTrue(result.isEmpty());
        verify(orderRepository).findByIdAndShipper(99L, shipper);
    }

    @ParameterizedTest
    @MethodSource("validTransitions")
    void updateStatus_acceptsValidTransitions(
            Order.OrderStatus currentStatus,
            Order.OrderStatus newStatus) {
        AppUser shipper = createShipper();
        Order order = createOrder(shipper, currentStatus);

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        if (newStatus == Order.OrderStatus.DELIVERED) {
            when(deliveryProofService.hasCompleteProof(order))
                    .thenReturn(true);
        }

        Order result = trackingService.updateStatus(1L, "nguyentai", newStatus,
                newStatus == Order.OrderStatus.FAILED
                        ? vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason.CUSTOMER_ABSENT : null,
                "", null);

        assertEquals(newStatus, result.getStatus());
        verify(orderRepository).save(order);
        verify(eventPublisher).publishEvent(
                any(OrderStatusChangedEvent.class));
    }

    @Test
    void updateStatus_rejectsInvalidTransition() {
        AppUser shipper = createShipper();
        Order order = createOrder(
                shipper, Order.OrderStatus.PENDING_PICKUP);

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));

        assertThrows(
                IllegalStateException.class,
                () -> trackingService.updateStatus(
                        1L,
                        "nguyentai",
                        Order.OrderStatus.DELIVERED));

        verify(orderRepository, never()).save(order);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateStatus_rejectsOrderAssignedToAnotherShipper() {
        AppUser shipper = createShipper();

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(99L, shipper))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> trackingService.updateStatus(
                        99L,
                        "nguyentai",
                        Order.OrderStatus.PICKED_UP));

        verify(orderRepository, never()).save(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateStatus_rejectsDeliveredWithoutCompleteProof() {
        AppUser shipper = createShipper();
        Order order = createOrder(
                shipper, Order.OrderStatus.IN_TRANSIT);

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));

        assertThrows(
                IllegalStateException.class,
                () -> trackingService.updateStatus(
                        1L,
                        "nguyentai",
                        Order.OrderStatus.DELIVERED));

        verify(deliveryProofService).hasCompleteProof(order);
        verify(orderRepository, never()).save(order);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateStatus_marksCodAsPaidWhenDelivered() {
        AppUser shipper = createShipper();
        Order order = createOrder(
                shipper, Order.OrderStatus.IN_TRANSIT);

        OrderPayment payment = new OrderPayment();
        payment.setOrder(order);
        payment.setMethod(OrderPayment.PaymentMethod.COD);

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(deliveryProofService.hasCompleteProof(order))
                .thenReturn(true);
        when(orderPaymentService.findByOrder(order))
                .thenReturn(Optional.of(payment));

        trackingService.updateStatus(
                1L,
                "nguyentai",
                Order.OrderStatus.DELIVERED);

        verify(orderPaymentService).markCodCollected(1L);
    }

    @Test
    void updateStatus_doesNotCollectPaymentForOnlineWallet() {
        AppUser shipper = createShipper();
        Order order = createOrder(
                shipper, Order.OrderStatus.IN_TRANSIT);

        OrderPayment payment = new OrderPayment();
        payment.setOrder(order);
        payment.setMethod(OrderPayment.PaymentMethod.VNPAY);

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(deliveryProofService.hasCompleteProof(order))
                .thenReturn(true);
        when(orderPaymentService.findByOrder(order))
                .thenReturn(Optional.of(payment));

        trackingService.updateStatus(
                1L,
                "nguyentai",
                Order.OrderStatus.DELIVERED);

        verify(orderPaymentService, never()).markCodCollected(1L);
    }

    @Test
    void findAssignedOrders_rejectsNonShipperAccount() {
        AppUser user = new AppUser();
        user.setUsername("khachhang11");
        user.setRole(AppUser.Role.USER);

        when(appUserRepository.findByUsername("khachhang11"))
                .thenReturn(Optional.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> trackingService.findAssignedOrders("khachhang11"));

        verify(orderRepository, never()).findByShipper(user);
    }

    private static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(
                        Order.OrderStatus.PENDING_PICKUP,
                        Order.OrderStatus.PICKED_UP),
                Arguments.of(
                        Order.OrderStatus.PICKED_UP,
                        Order.OrderStatus.IN_TRANSIT),
                Arguments.of(
                        Order.OrderStatus.IN_TRANSIT,
                        Order.OrderStatus.DELIVERED),
                Arguments.of(
                        Order.OrderStatus.IN_TRANSIT,
                        Order.OrderStatus.FAILED),
                Arguments.of(
                        Order.OrderStatus.FAILED,
                        Order.OrderStatus.RETURNED));
    }

    @Test
    void interprovincialPickupLegCannotStartDelivery() {
        AppUser shipper = createShipper();
        Order order = createOrder(shipper, Order.OrderStatus.PICKED_UP);
        when(appUserRepository.findByUsername("nguyentai")).thenReturn(java.util.Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper)).thenReturn(java.util.Optional.of(order));
        var route = route(order, "SGN", "HAN");
        when(routes.findByOrder_Id(1L)).thenReturn(java.util.Optional.of(route));
        var error = assertThrows(IllegalStateException.class,
                () -> trackingService.updateStatus(1L, "nguyentai", Order.OrderStatus.IN_TRANSIT));
        assertTrue(error.getMessage().contains("bàn giao vào kho"));
        assertEquals(Order.OrderStatus.PICKED_UP, order.getStatus());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void intraprovincialDeliveryMovesRouteToLastMile() {
        AppUser shipper = createShipper();
        Order order = createOrder(shipper, Order.OrderStatus.PICKED_UP);
        when(appUserRepository.findByUsername("nguyentai")).thenReturn(java.util.Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        var route = route(order, "SGN", "SGN");
        when(routes.findByOrder_Id(1L)).thenReturn(java.util.Optional.of(route));
        trackingService.updateStatus(1L, "nguyentai", Order.OrderStatus.IN_TRANSIT);
        assertEquals(vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.LAST_MILE, route.getStage());
        assertEquals(Order.OrderStatus.IN_TRANSIT, order.getStatus());
    }

    @Test
    void handoverToHubReleasesShipperAndMovesToOriginHub() {
        AppUser shipper = createShipper();
        Order order = createOrder(shipper, Order.OrderStatus.PICKED_UP);
        when(appUserRepository.findByUsername("nguyentai")).thenReturn(java.util.Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        var route = route(order, "SGN", "HAN");
        when(routes.findByOrder_Id(1L)).thenReturn(java.util.Optional.of(route));
        trackingService.handoverToHub(1L, "nguyentai");
        assertEquals(vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.ORIGIN_HUB, route.getStage());
        assertNull(order.getShipper());
        assertEquals(Order.OrderStatus.PICKED_UP, order.getStatus());
        verify(history).save(any());
    }

    @Test
    void handoverRejectsIntraprovincialOrder() {
        AppUser shipper = createShipper();
        Order order = createOrder(shipper, Order.OrderStatus.PICKED_UP);
        when(appUserRepository.findByUsername("nguyentai")).thenReturn(java.util.Optional.of(shipper));
        when(orderRepository.findLockedByIdAndShipper(1L, shipper)).thenReturn(java.util.Optional.of(order));
        when(routes.findByOrder_Id(1L)).thenReturn(java.util.Optional.of(route(order, "SGN", "SGN")));
        assertThrows(IllegalStateException.class, () -> trackingService.handoverToHub(1L, "nguyentai"));
        assertSame(shipper, order.getShipper());
    }

    private vn.edu.hcmute.uteexpress.entity.routing.OrderRoute route(Order order, String from, String to) {
        var origin = branch(from.equals("SGN") ? 1L : 2L, from);
        var dest = from.equals(to) ? origin : branch(2L, to);
        var pickup = new vn.edu.hcmute.uteexpress.entity.routing.ProvinceArea("79", from + "-A", "Khu A", origin,
                java.math.BigDecimal.ONE, java.math.BigDecimal.ONE);
        var delivery = new vn.edu.hcmute.uteexpress.entity.routing.ProvinceArea("01", to + "-B", "Khu B", dest,
                java.math.BigDecimal.ONE, java.math.BigDecimal.ONE);
        return new vn.edu.hcmute.uteexpress.entity.routing.OrderRoute(order, pickup, delivery);
    }

    private vn.edu.hcmute.uteexpress.entity.routing.Branch branch(Long id, String code) {
        var branch = new vn.edu.hcmute.uteexpress.entity.routing.Branch(code, "Kho " + code, "79", "Địa chỉ", code + "-A",
                java.time.LocalDateTime.of(2026, 10, 1, 8, 0));
        org.springframework.test.util.ReflectionTestUtils.setField(branch, "id", id);
        return branch;
    }

    private AppUser createShipper() {
        AppUser shipper = new AppUser();
        shipper.setUsername("nguyentai");
        shipper.setRole(AppUser.Role.SHIPPER);
        shipper.setEnabled(true);
        return shipper;
    }

    private Order createOrder(
            AppUser shipper,
            Order.OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setTrackingCode("UTE64477413");
        order.setShipper(shipper);
        order.setStatus(status);
        return order;
    }
}
