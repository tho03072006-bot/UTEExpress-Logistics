package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsPeriod;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsResponse;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.tracking.ShipperStatisticsRepository;
import vn.edu.hcmute.uteexpress.service.tracking.ShipperStatisticsService;

@Service
@Transactional(readOnly = true)
public class ShipperStatisticsServiceImpl
        implements ShipperStatisticsService {

    private final AppUserRepository appUserRepository;
    private final ShipperStatisticsRepository statisticsRepository;
    private final Clock clock;

    public ShipperStatisticsServiceImpl(
            AppUserRepository appUserRepository,
            ShipperStatisticsRepository statisticsRepository,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.statisticsRepository = statisticsRepository;
        this.clock = clock;
    }

    @Override
    public ShipperStatisticsResponse getStatistics(
            String shipperUsername,
            ShipperStatisticsPeriod period) {
        AppUser shipper = findShipper(shipperUsername);
        ShipperStatisticsPeriod selectedPeriod = period == null
                ? ShipperStatisticsPeriod.DAY
                : period;

        DateRange range = dateRange(
                LocalDate.now(clock), selectedPeriod);
        LocalDateTime fromInclusive = range.fromDate().atStartOfDay();
        LocalDateTime toExclusive = range.toExclusive().atStartOfDay();

        List<Order> orders = statisticsRepository
                .findByShipperAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                        shipper, fromInclusive, toExclusive);

        long deliveredOrders = countStatus(
                orders, Order.OrderStatus.DELIVERED);
        long failedOrReturnedOrders = orders.stream()
                .filter(order -> order.getStatus()
                        == Order.OrderStatus.FAILED
                        || order.getStatus()
                        == Order.OrderStatus.RETURNED)
                .count();
        long inProgressOrders = orders.stream()
                .filter(order -> order.getStatus()
                        == Order.OrderStatus.PENDING_PICKUP
                        || order.getStatus()
                        == Order.OrderStatus.PICKED_UP
                        || order.getStatus()
                        == Order.OrderStatus.IN_TRANSIT)
                .count();

        BigDecimal collectedCod = statisticsRepository.sumCollectedCod(
                shipper,
                OrderPayment.PaymentMethod.COD,
                OrderPayment.PaymentStatus.PAID,
                fromInclusive,
                toExclusive);

        return new ShipperStatisticsResponse(
                selectedPeriod,
                range.fromDate(),
                range.toExclusive().minusDays(1),
                orders.size(),
                deliveredOrders,
                failedOrReturnedOrders,
                inProgressOrders,
                successRate(deliveredOrders, orders.size()),
                collectedCod == null ? BigDecimal.ZERO : collectedCod);
    }

    private DateRange dateRange(
            LocalDate today,
            ShipperStatisticsPeriod period) {
        LocalDate fromDate = switch (period) {
            case DAY -> today;
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(
                    DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
        };

        LocalDate toExclusive = switch (period) {
            case DAY -> fromDate.plusDays(1);
            case WEEK -> fromDate.plusWeeks(1);
            case MONTH -> fromDate.plusMonths(1);
        };

        return new DateRange(fromDate, toExclusive);
    }

    private long countStatus(
            List<Order> orders,
            Order.OrderStatus status) {
        return orders.stream()
                .filter(order -> order.getStatus() == status)
                .count();
    }

    private BigDecimal successRate(
            long deliveredOrders,
            long totalOrders) {
        if (totalOrders == 0) {
            return BigDecimal.ZERO.setScale(1);
        }

        return BigDecimal.valueOf(deliveredOrders)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(totalOrders),
                        1,
                        RoundingMode.HALF_UP);
    }

    private AppUser findShipper(String shipperUsername) {
        AppUser shipper = appUserRepository
                .findByUsername(shipperUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản Shipper."));

        if (shipper.getRole() != AppUser.Role.SHIPPER) {
            throw new IllegalStateException(
                    "Tài khoản hiện tại không có vai trò SHIPPER.");
        }

        return shipper;
    }

    private record DateRange(
            LocalDate fromDate,
            LocalDate toExclusive) {
    }
}
