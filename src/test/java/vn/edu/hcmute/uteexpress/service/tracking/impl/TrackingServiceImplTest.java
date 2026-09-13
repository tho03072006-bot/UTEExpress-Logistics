package vn.edu.hcmute.uteexpress.service.tracking.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class TrackingServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private OrderRepository orderRepository;

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
        Order assignedOrder = new Order();
        assignedOrder.setId(1L);
        assignedOrder.setShipper(shipper);

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

    private AppUser createShipper() {
        AppUser shipper = new AppUser();
        shipper.setUsername("nguyentai");
        shipper.setRole(AppUser.Role.SHIPPER);
        return shipper;
    }
}