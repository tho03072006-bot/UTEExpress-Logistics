package vn.edu.hcmute.uteexpress.service.tracking.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;

@ExtendWith(MockitoExtension.class)
class TrackingServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderPaymentService orderPaymentService;

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
        when(orderRepository.findByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = trackingService.updateStatus(
                1L, "nguyentai", newStatus);

        assertEquals(newStatus, result.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void updateStatus_rejectsInvalidTransition() {
        AppUser shipper = createShipper();
        Order order = createOrder(
                shipper, Order.OrderStatus.PENDING_PICKUP);

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));

        assertThrows(
                IllegalStateException.class,
                () -> trackingService.updateStatus(
                        1L,
                        "nguyentai",
                        Order.OrderStatus.DELIVERED));

        verify(orderRepository, never()).save(order);
    }

    @Test
    void updateStatus_rejectsOrderAssignedToAnotherShipper() {
        AppUser shipper = createShipper();

        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
        when(orderRepository.findByIdAndShipper(99L, shipper))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> trackingService.updateStatus(
                        99L,
                        "nguyentai",
                        Order.OrderStatus.PICKED_UP));

        verify(orderRepository, never()).save(any(Order.class));
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
        when(orderRepository.findByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
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
        when(orderRepository.findByIdAndShipper(1L, shipper))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
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

    private AppUser createShipper() {
        AppUser shipper = new AppUser();
        shipper.setUsername("nguyentai");
        shipper.setRole(AppUser.Role.SHIPPER);
        return shipper;
    }

    private Order createOrder(
            AppUser shipper,
            Order.OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setShipper(shipper);
        order.setStatus(status);
        return order;
    }
}