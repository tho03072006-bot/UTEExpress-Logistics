package vn.edu.hcmute.uteexpress.service.manager.impl;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.service.manager.ManagerDispatchService;
import vn.edu.hcmute.uteexpress.service.tracking.DispatchService;

/** Tương thích API cũ; mọi đường phân công đều dùng chung DispatchService. */
@Service
public class ManagerDispatchServiceImpl implements ManagerDispatchService {
    private final DispatchService dispatch;
    public ManagerDispatchServiceImpl(DispatchService dispatch) { this.dispatch = dispatch; }
    @Transactional(readOnly = true)
    public Page<DispatchOrderResponse> findPendingOrders(String actor, int page) {
        return dispatch.pending(actor, page(page)).map(DispatchOrderResponse::from);
    }
    @Transactional(readOnly = true)
    public Page<ShipperWorkloadResponse> findShippers(String actor, int page) {
        return dispatch.shippers(actor, page(page)).map(s -> new ShipperWorkloadResponse(s.getId(), s.getUsername(), s.getFullName(), dispatch.load(s.getId())));
    }
    /** Giữ giới hạn trang của API cũ (0–10000), kiểm tra trước khi truy vấn. */
    private static int page(int page) {
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Số trang phải từ 0 đến 10000.");
        return page;
    }
    public void assignOrder(String actor, Long orderId, OrderAssignmentRequest request) { dispatch.assignById(actor, orderId, request); }
}
