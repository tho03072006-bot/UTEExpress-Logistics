package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.tracking.ShipperStatisticsRepository;
import vn.edu.hcmute.uteexpress.service.tracking.ShipperStatisticsService;

@Service
@Transactional(readOnly = true)
public class ShipperStatisticsServiceImpl implements ShipperStatisticsService {
    private final AppUserRepository users;
    private final ShipperStatisticsRepository repository;
    private final OrderStatusHistoryRepository history;
    private final Clock clock;
    public ShipperStatisticsServiceImpl(AppUserRepository users, ShipperStatisticsRepository repository,
            OrderStatusHistoryRepository history, Clock clock) {
        this.users = users; this.repository = repository; this.history = history; this.clock = clock;
    }
    @Override public ShipperStatisticsResponse getStatistics(String username, ShipperStatisticsPeriod period) {
        return getStatistics(username, period, null, null);
    }
    @Override public ShipperStatisticsResponse getStatistics(String username, ShipperStatisticsPeriod period, LocalDate from, LocalDate to) {
        var shipper = users.findByUsername(username).filter(u -> u.isEnabled() && u.getRole() == AppUser.Role.SHIPPER)
                .orElseThrow(() -> new IllegalStateException("Tài khoản hiện tại không có vai trò Shipper đang hoạt động."));
        var selected = period == null ? ShipperStatisticsPeriod.DAY : period;
        var today = LocalDate.now(clock);
        if ((from == null) != (to == null)) throw new IllegalArgumentException("Hãy chọn cả từ ngày và đến ngày.");
        if (from == null) {
            from = switch (selected) { case DAY -> today; case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); case MONTH -> today.withDayOfMonth(1); };
            to = switch (selected) { case DAY -> from; case WEEK -> from.plusDays(6); case MONTH -> from.with(TemporalAdjusters.lastDayOfMonth()); };
        }
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 91 || from.getYear() < 2000 || to.getYear() > 2100)
            throw new IllegalArgumentException("Khoảng thống kê tối đa 92 ngày, trong năm 2000–2100.");
        var start = from.atStartOfDay(); var end = to.plusDays(1).atStartOfDay();
        var activities = new ArrayList<>(history.activities(shipper, start, end));
        var legacy = repository.findByShipperAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(shipper, start, end);
        for (var order : legacy) activities.add(new ShipperActivity(order.getId(), order.getStatus(), order.getUpdatedAt()));
        activities.sort(Comparator.comparing(ShipperActivity::at));
        var last = new HashMap<Long, Order.OrderStatus>();
        var buckets = new TreeMap<LocalDate, long[]>();
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) buckets.put(date, new long[3]);
        for (var activity : activities) {
            last.put(activity.orderId(), activity.status());
            var counts = buckets.get(activity.at().toLocalDate());
            if (counts == null) continue;
            switch (activity.status()) { case DELIVERED -> counts[0]++; case FAILED -> counts[1]++; case RETURNED -> counts[2]++; default -> { } }
        }
        var codByDay = new HashMap<LocalDate, BigDecimal>();
        for (var row : repository.dailyCod(shipper, start, end)) codByDay.put(row.getDate(), row.getAmount());
        var days = buckets.entrySet().stream().map(e -> new ShipperDailyStatistics(e.getKey(), e.getValue()[0],
                e.getValue()[1], e.getValue()[2], codByDay.getOrDefault(e.getKey(), BigDecimal.ZERO))).toList();
        long delivered = last.values().stream().filter(s -> s == Order.OrderStatus.DELIVERED).count();
        long unsuccessful = last.values().stream().filter(s -> s == Order.OrderStatus.FAILED || s == Order.OrderStatus.RETURNED).count();
        long active = last.values().stream().filter(s -> s == Order.OrderStatus.PENDING_PICKUP || s == Order.OrderStatus.PICKED_UP || s == Order.OrderStatus.IN_TRANSIT).count();
        var cod = repository.sumCollectedCod(shipper, OrderPayment.PaymentMethod.COD, OrderPayment.PaymentStatus.PAID, start, end);
        var rate = last.isEmpty() ? BigDecimal.ZERO.setScale(1) : BigDecimal.valueOf(delivered).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(last.size()), 1, RoundingMode.HALF_UP);
        return new ShipperStatisticsResponse(selected, from, to, last.size(), delivered, unsuccessful, active,
                rate, cod == null ? BigDecimal.ZERO : cod, days, legacy.size());
    }
    @Override public byte[] exportCsv(String username, ShipperStatisticsPeriod period, LocalDate from, LocalDate to) {
        var result = getStatistics(username, period, from, to);
        var csv = new StringBuilder("\uFEFFNgày,Giao thành công,Lượt giao thất bại,Hoàn trả,Cước COD đã thu\r\n");
        for (var day : result.days()) csv.append(day.date()).append(',').append(day.delivered()).append(',')
                .append(day.failed()).append(',').append(day.returned()).append(',').append(day.collectedCod().toPlainString()).append("\r\n");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
