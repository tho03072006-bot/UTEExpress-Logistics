package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.manager.*;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.service.manager.*;

@Service @Transactional
public class ManagerReportServiceImpl implements ManagerReportService {
    private final ManagerDispatchRepository orders;
    private final StaffAccessPolicy access;
    private final Clock clock;
    public ManagerReportServiceImpl(ManagerDispatchRepository orders, StaffAccessPolicy access, Clock clock) {
        this.orders = orders; this.access = access; this.clock = clock;
    }
    @Override @Transactional(readOnly = true)
    public OperationsReportResponse report(String actor, LocalDate from, LocalDate to) {
        access.requireOperator(actor);
        if (from == null || to == null || from.isBefore(LocalDate.of(2000, 1, 1)) || to.isAfter(LocalDate.now(clock))
            || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new IllegalArgumentException("Chọn khoảng ngày hợp lệ, không ở tương lai, tối đa 366 ngày.");
        }
        var counts = new EnumMap<Order.OrderStatus, Long>(Order.OrderStatus.class);
        for (var status : Order.OrderStatus.values()) { counts.put(status, 0L); }
        orders.countStatuses(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
            .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long delivered = counts.get(Order.OrderStatus.DELIVERED);
        // FAILED còn có thể giao lại/hoàn, chưa phải kết quả cuối cùng.
        long completed = delivered + counts.get(Order.OrderStatus.RETURNED);
        int rate = completed == 0 ? 0 : (int) Math.round(delivered * 100.0 / completed);
        var fees = orders.sumPaidFees(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return new OperationsReportResponse(from, to, Collections.unmodifiableMap(counts), total, rate,
            fees == null ? BigDecimal.ZERO : fees);
    }
}

