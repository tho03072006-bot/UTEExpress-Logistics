package vn.edu.hcmute.uteexpress.dto.manager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import vn.edu.hcmute.uteexpress.entity.Order;
public record OperationsReportResponse(LocalDate from, LocalDate to, Map<Order.OrderStatus, Long> counts,
        long totalOrders, BigDecimal completionRate, BigDecimal paidShippingFees) { }

