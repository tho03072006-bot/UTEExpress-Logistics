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
    private final vn.edu.hcmute.uteexpress.service.routing.BranchScope scope;
    public ManagerReportServiceImpl(ManagerDispatchRepository orders, StaffAccessPolicy access, Clock clock,
            vn.edu.hcmute.uteexpress.service.routing.BranchScope scope) {
        this.orders = orders; this.access = access; this.clock = clock; this.scope = scope;
    }
    @Override @Transactional(readOnly = true)
    public java.util.List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor) { return scope.branches(actor); }
    @Override @Transactional(readOnly = true)
    public OperationsReportResponse report(String actor, LocalDate from, LocalDate to, Long branchId) {
        access.requireOperator(actor);
        if (from == null || to == null || from.isBefore(LocalDate.of(2000, 1, 1)) || to.isAfter(LocalDate.now(clock))
            || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new IllegalArgumentException("Chọn khoảng ngày hợp lệ, không ở tương lai, tối đa 366 ngày.");
        }
        Long branch = scope.of(actor, branchId);
        var counts = new EnumMap<Order.OrderStatus, Long>(Order.OrderStatus.class);
        for (var status : Order.OrderStatus.values()) { counts.put(status, 0L); }
        orders.countStatuses(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), branch)
            .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long delivered = counts.get(Order.OrderStatus.DELIVERED);
        // FAILED còn có thể giao lại/hoàn, chưa phải kết quả cuối cùng.
        var rate = vn.edu.hcmute.uteexpress.util.DeliverySuccessRate.calculate(delivered, counts.get(Order.OrderStatus.RETURNED));
        var fees = orders.sumPaidFees(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), branch);
        return new OperationsReportResponse(from, to, Collections.unmodifiableMap(counts), total, rate,
            fees == null ? BigDecimal.ZERO : fees);
    }
}

