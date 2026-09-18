package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.ManagerOrderResponse;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderReadRepository;
import vn.edu.hcmute.uteexpress.service.manager.ManagerOrderService;

@Service
@Transactional(readOnly = true)
public class ManagerOrderServiceImpl implements ManagerOrderService {
    private static final int PAGE_SIZE = 20;
    private static final long MAX_RANGE_DAYS = 366;
    private final ManagerOrderReadRepository orders;
    private final Clock clock;
    public ManagerOrderServiceImpl(ManagerOrderReadRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    public Page<ManagerOrderResponse> findOrders(String tracking, Order.OrderStatus status,
            LocalDate from, LocalDate to, int page) {
        String code = tracking == null ? "" : tracking.strip();
        if (!code.isEmpty() && !code.matches("[A-Za-z0-9-]{1,20}")) {
            throw new IllegalArgumentException("Mã vận đơn tối đa 20 ký tự, chỉ gồm chữ, số hoặc gạch ngang.");
        }
        LocalDate today = LocalDate.now(clock);
        if (from == null || to == null || from.isAfter(to) || to.isAfter(today)
                || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS
                || from.getYear() < 2000) {
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ: từ năm 2000, ngày bắt đầu không sau ngày kết thúc, không ở tương lai và tối đa 366 ngày.");
        }
        if (page < 0 || page > 10000) {
            throw new IllegalArgumentException("Số trang phải từ 0 đến 10000.");
        }
        return orders.findOrders(code, status, from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                PageRequest.of(page, PAGE_SIZE, Sort.by("createdAt").descending().and(Sort.by("id").descending())))
                .map(ManagerOrderResponse::from);
    }
}

