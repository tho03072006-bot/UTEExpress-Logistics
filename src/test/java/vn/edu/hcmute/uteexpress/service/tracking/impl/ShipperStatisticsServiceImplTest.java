package vn.edu.hcmute.uteexpress.service.tracking.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsPeriod;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsResponse;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.tracking.ShipperStatisticsRepository;
import vn.edu.hcmute.uteexpress.service.tracking.ShipperStatisticsService;

class ShipperStatisticsServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-13T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ShipperStatisticsRepository statisticsRepository;

    private ShipperStatisticsService statisticsService;
    private AppUser shipper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        statisticsService = new ShipperStatisticsServiceImpl(
                appUserRepository,
                statisticsRepository,
                FIXED_CLOCK);

        shipper = new AppUser();
        shipper.setUsername("nguyentai");
        shipper.setRole(AppUser.Role.SHIPPER);
        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(shipper));
    }

    @Test
    void getStatistics_calculatesCountsRateAndCollectedCod() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 13, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 14, 0, 0);
        List<Order> orders = List.of(
                order(Order.OrderStatus.DELIVERED),
                order(Order.OrderStatus.DELIVERED),
                order(Order.OrderStatus.FAILED),
                order(Order.OrderStatus.RETURNED),
                order(Order.OrderStatus.IN_TRANSIT));

        when(statisticsRepository
                .findByShipperAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                        shipper, from, to))
                .thenReturn(orders);
        when(statisticsRepository.sumCollectedCod(
                shipper,
                OrderPayment.PaymentMethod.COD,
                OrderPayment.PaymentStatus.PAID,
                from,
                to))
                .thenReturn(new BigDecimal("62000"));

        ShipperStatisticsResponse result = statisticsService.getStatistics(
                "nguyentai", ShipperStatisticsPeriod.DAY);

        assertEquals(5, result.totalOrders());
        assertEquals(2, result.deliveredOrders());
        assertEquals(2, result.failedOrReturnedOrders());
        assertEquals(1, result.inProgressOrders());
        assertEquals(new BigDecimal("40.0"), result.successRate());
        assertEquals(new BigDecimal("62000"), result.collectedCod());
    }

    @Test
    void getStatistics_usesMondayToSundayForWeek() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 7, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 14, 0, 0);
        stubEmptyPeriod(from, to);

        ShipperStatisticsResponse result = statisticsService.getStatistics(
                "nguyentai", ShipperStatisticsPeriod.WEEK);

        assertEquals("2026-09-07", result.fromDate().toString());
        assertEquals("2026-09-13", result.toDate().toString());
        verify(statisticsRepository)
                .findByShipperAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                        shipper, from, to);
    }

    @Test
    void getStatistics_usesFirstAndLastDayForMonth() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 1, 0, 0);
        stubEmptyPeriod(from, to);

        ShipperStatisticsResponse result = statisticsService.getStatistics(
                "nguyentai", ShipperStatisticsPeriod.MONTH);

        assertEquals("2026-09-01", result.fromDate().toString());
        assertEquals("2026-09-30", result.toDate().toString());
        verify(statisticsRepository).sumCollectedCod(
                shipper,
                OrderPayment.PaymentMethod.COD,
                OrderPayment.PaymentStatus.PAID,
                from,
                to);
    }

    @Test
    void getStatistics_returnsZeroRateWhenPeriodHasNoOrders() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 13, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 14, 0, 0);
        stubEmptyPeriod(from, to);

        ShipperStatisticsResponse result = statisticsService.getStatistics(
                "nguyentai", null);

        assertEquals(ShipperStatisticsPeriod.DAY, result.period());
        assertEquals(new BigDecimal("0.0"), result.successRate());
        assertEquals(BigDecimal.ZERO, result.collectedCod());
    }

    @Test
    void getStatistics_rejectsNonShipperAccount() {
        AppUser user = new AppUser();
        user.setUsername("khachhang11");
        user.setRole(AppUser.Role.USER);
        when(appUserRepository.findByUsername("khachhang11"))
                .thenReturn(Optional.of(user));

        assertThrows(
                IllegalStateException.class,
                () -> statisticsService.getStatistics(
                        "khachhang11", ShipperStatisticsPeriod.DAY));

        verify(statisticsRepository, never())
                .findByShipperAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private void stubEmptyPeriod(
            LocalDateTime from,
            LocalDateTime to) {
        when(statisticsRepository
                .findByShipperAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                        shipper, from, to))
                .thenReturn(List.of());
        when(statisticsRepository.sumCollectedCod(
                shipper,
                OrderPayment.PaymentMethod.COD,
                OrderPayment.PaymentStatus.PAID,
                from,
                to))
                .thenReturn(BigDecimal.ZERO);
    }

    private Order order(Order.OrderStatus status) {
        Order order = new Order();
        order.setShipper(shipper);
        order.setStatus(status);
        return order;
    }
}
