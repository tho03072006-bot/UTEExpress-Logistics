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

    /** Đơn kết thúc khác (vd. đã hủy) để biểu đồ tròn đủ 100%. */
    public long getOtherOrders() {
        return Math.max(0, totalOrders - deliveredOrders - failedOrReturnedOrders - inProgressOrders);
    }

    /** Phần trăm cộng dồn cho biểu đồ tròn (conic-gradient): thành công | thất bại | đang xử lý | khác. */
    public double getDeliveredEnd() { return percent(deliveredOrders); }
    public double getFailedEnd() { return percent(deliveredOrders + failedOrReturnedOrders); }
    public double getInProgressEnd() { return percent(deliveredOrders + failedOrReturnedOrders + inProgressOrders); }

    /** Số lượt giao thành công trung bình mỗi ngày trong kỳ (1 chữ số thập phân). */
    public double getDailyAverage() {
        if (days.isEmpty()) return 0;
        double total = days.stream().mapToLong(ShipperDailyStatistics::delivered).sum();
        return Math.round(total * 10.0 / days.size()) / 10.0;
    }

    /** Ngày có nhiều lượt giao thành công nhất; null khi cả kỳ chưa có lượt nào. */
    public ShipperDailyStatistics getBestDay() {
        return days.stream().filter(d -> d.delivered() > 0)
                .max(java.util.Comparator.comparingLong(ShipperDailyStatistics::delivered)).orElse(null);
    }

    private double percent(long value) {
        return totalOrders == 0 ? 0 : Math.round(value * 1000.0 / totalOrders) / 10.0;
    }
}
