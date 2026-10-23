package vn.edu.hcmute.uteexpress.service.manager;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.manager.ManagerOrderResponse;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface ManagerOrderService {
    /** Manager chỉ thấy đơn gửi đi hoặc giao tới chi nhánh mình; Admin xem tất cả hoặc lọc một chi nhánh. */
    Page<ManagerOrderResponse> findOrders(String actor, Long branchId, String tracking, Order.OrderStatus status,
            LocalDate from, LocalDate to, int page);
    java.util.List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor);
}

