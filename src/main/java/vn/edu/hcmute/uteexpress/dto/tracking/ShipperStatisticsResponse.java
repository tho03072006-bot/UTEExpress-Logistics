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
        BigDecimal collectedCod) {
}
