package vn.edu.hcmute.uteexpress.service.manager;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.manager.ManagerOrderResponse;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface ManagerOrderService {
    Page<ManagerOrderResponse> findOrders(String tracking, Order.OrderStatus status,
            LocalDate from, LocalDate to, int page);
}

