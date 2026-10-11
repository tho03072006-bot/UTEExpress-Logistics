package vn.edu.hcmute.uteexpress.dto.tracking;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ShipperStatisticsResponse(
        ShipperStatisticsPeriod period,
        LocalDate fromDate,
        LocalDate toDate,
        long totalOrders,
        long deliveredOrders,
        long failedOrReturnedOrders,
        long inProgressOrders,
        BigDecimal successRate,
        BigDecimal collectedCod,
        java.util.List<ShipperDailyStatistics> days,
        long legacyOrders) {
    public long getChartMax() {
        return Math.max(1, days.stream().mapToLong(d -> Math.max(d.delivered(), d.getUnsuccessful())).max().orElse(1));
    }
}
