package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.DashboardChartsResponse;
import vn.edu.hcmute.uteexpress.dto.manager.DashboardChartsResponse.CategoryResponse;
import vn.edu.hcmute.uteexpress.dto.manager.DashboardChartsResponse.DayResponse;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderStatisticsRepository;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccessPolicy;
import vn.edu.hcmute.uteexpress.service.manager.StaffDashboardChartService;

@Service
@Transactional(readOnly = true)
public class StaffDashboardChartServiceImpl implements StaffDashboardChartService {
    private static final int WINDOW_DAYS = 14;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final ManagerOrderStatisticsRepository orders;
    private final StaffAccessPolicy access;
    private final Clock clock;

    public StaffDashboardChartServiceImpl(ManagerOrderStatisticsRepository orders,
            StaffAccessPolicy access, Clock clock) {
        this.orders = orders; this.access = access; this.clock = clock;
    }

    @Override public DashboardChartsResponse getOperationsCharts(String actor) {
        access.requireOperator(actor);
        return buildCharts();
    }

    @Override public DashboardChartsResponse getAdminCharts(String actor) {
        access.requireAdmin(actor);
        return buildCharts();
    }

    private DashboardChartsResponse buildCharts() {
        var generatedAt = LocalDateTime.now(clock);
        var to = generatedAt.toLocalDate();
        var from = to.minusDays(WINDOW_DAYS - 1);
        var statuses = new EnumMap<Order.OrderStatus, Long>(Order.OrderStatus.class);
        for (var row : orders.countGroupedStatuses()) {
            if (row.getStatus() == null || statuses.put(row.getStatus(), validCount(row.getTotal())) != null)
                throw new IllegalStateException("Số liệu trạng thái đơn không nhất quán.");
        }
        var statusCounts = Arrays.stream(Order.OrderStatus.values()).map(status ->
                new Count(status.name(), statusLabel(status), statusTone(status), statuses.getOrDefault(status, 0L))).toList();
        long totalOrders = total(statusCounts);

        var days = new TreeMap<LocalDate, Long>();
        for (int i = 0; i < WINDOW_DAYS; i++) days.put(from.plusDays(i), 0L);
        var seen = new HashSet<LocalDate>();
        for (var row : orders.countCreatedByDay(from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            var date = LocalDate.of(row.getYear(), row.getMonth(), row.getDay());
            if (!days.containsKey(date) || !seen.add(date))
                throw new IllegalStateException("Số liệu ngày nằm ngoài kỳ hoặc bị trùng.");
            days.put(date, validCount(row.getTotal()));
        }
        long peak = days.values().stream().mapToLong(Long::longValue).max().orElse(0);
        long newOrders = days.values().stream().reduce(0L, Math::addExact);
        var dailySeries = days.entrySet().stream().map(day ->
                new DayResponse(day.getKey(), day.getValue(), percentage(day.getValue(), peak))).toList();

        return new DashboardChartsResponse(from, to, generatedAt, categories(statusCounts, totalOrders), dailySeries,
                totalOrders, newOrders, peak);
    }

    private static List<CategoryResponse> categories(List<Count> counts, long total) {
        var result = new ArrayList<CategoryResponse>();
        for (var row : counts) {
            result.add(new CategoryResponse(row.code(), row.label(), row.tone(), row.count(),
                    percentage(row.count(), total)));
        }
        return List.copyOf(result);
    }

    private static long total(List<Count> counts) {
        return counts.stream().map(Count::count).reduce(0L, Math::addExact);
    }

    private static long validCount(long count) {
        if (count < 0) throw new IllegalStateException("Số liệu thống kê không được âm.");
        return count;
    }

    private static BigDecimal percentage(long count, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(count).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static String statusLabel(Order.OrderStatus status) {
        return switch (status) {
            case PENDING_PICKUP -> "Chờ lấy hàng";
            case PICKED_UP -> "Đã lấy hàng";
            case IN_TRANSIT -> "Đang vận chuyển";
            case DELIVERED -> "Giao thành công";
            case FAILED -> "Giao thất bại";
            case CANCELLED -> "Đã hủy";
            case RETURNED -> "Đã hoàn hàng";
        };
    }

    private static String statusTone(Order.OrderStatus status) {
        return switch (status) {
            case PENDING_PICKUP -> "amber";
            case PICKED_UP -> "cyan";
            case IN_TRANSIT -> "blue";
            case DELIVERED -> "green";
            case FAILED -> "rose";
            case CANCELLED -> "slate";
            case RETURNED -> "violet";
        };
    }

    private record Count(String code, String label, String tone, long count) { }
}
