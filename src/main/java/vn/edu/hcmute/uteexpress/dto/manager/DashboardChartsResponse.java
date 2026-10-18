package vn.edu.hcmute.uteexpress.dto.manager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Chỉ chứa số liệu tổng hợp, không chứa hồ sơ khách hàng hay thông tin xác thực. */
public record DashboardChartsResponse(LocalDate fromDate, LocalDate toDate, LocalDateTime generatedAt,
        List<CategoryResponse> orderStatuses, List<DayResponse> days,
        long totalOrders, long totalNewOrders, long peakDailyOrders) {

    public DashboardChartsResponse {
        orderStatuses = List.copyOf(orderStatuses);
        days = List.copyOf(days);
    }

    public record CategoryResponse(String code, String label, String tone, long count,
            BigDecimal percentage) { }

    public record DayResponse(LocalDate date, long count, BigDecimal heightPercentage) {
        public String getDisplayCount() {
            if (count < 1000) return Long.toString(count);
            long divisor = count >= 1_000_000_000L ? 1_000_000_000L : count >= 1_000_000L ? 1_000_000L : 1000L;
            String suffix = divisor == 1_000_000_000L ? "tỷ" : divisor == 1_000_000L ? "tr" : "k";
            return BigDecimal.valueOf(count).divide(BigDecimal.valueOf(divisor), 1, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString().replace('.', ',') + suffix;
        }
    }
}
