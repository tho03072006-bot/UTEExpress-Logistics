package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.manager.*;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.service.manager.*;

@Service @Transactional
public class ManagerDispatchServiceImpl implements ManagerDispatchService {
    private final ManagerDispatchRepository orders;
    private final ManagerShipperRepository shippers;
    private final StaffAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.service.OrderPaymentService payments;
    public ManagerDispatchServiceImpl(ManagerDispatchRepository orders, ManagerShipperRepository shippers, StaffAccessPolicy access, AuditService audit, Clock clock,
            vn.edu.hcmute.uteexpress.service.OrderPaymentService payments) {
        this.orders = orders; this.shippers = shippers; this.access = access; this.audit = audit; this.clock = clock;
        this.payments = payments;
    }
    @Override @Transactional(readOnly = true)
    public Page<DispatchOrderResponse> findPendingOrders(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page);
        return orders.findByStatusOrderByCreatedAtAscIdAsc(Order.OrderStatus.PENDING_PICKUP, PageRequest.of(page, 20))
            .map(DispatchOrderResponse::from);
    }
    @Override @Transactional(readOnly = true)
    public Page<ShipperWorkloadResponse> findShippers(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page);
        return orders.findWorkloads(AppUser.Role.SHIPPER,
            List.of(Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.PICKED_UP, Order.OrderStatus.IN_TRANSIT),
            PageRequest.of(page, 100));
    }
    @Override
    public void assignOrder(String actor, Long orderId, OrderAssignmentRequest request) {
        access.requireOperator(actor); access.validateId(orderId); access.validate(request);
        var order = orders.lockOrder(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (order.getRevision() != request.getRevision()) {
            throw new StaffConflictException("Đơn vừa được cập nhật. Hãy tải lại trang.");
        }
        if (order.getStatus() != Order.OrderStatus.PENDING_PICKUP) {
            throw new StaffConflictException("Chỉ phân công lại trước khi lấy hàng. Trạng thái đơn đã thay đổi.");
        }
        payments.requireReadyForFulfillment(order);
        var shipper = shippers.lockShipper(request.getShipperId())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy shipper."));
        if (!shipper.isEnabled() || shipper.isLockedByAdmin() || shipper.getRole() != AppUser.Role.SHIPPER) {
            throw new IllegalArgumentException("Chỉ được chọn shipper đang hoạt động.");
        }
        if (order.getShipper() != null && order.getShipper().getId().equals(shipper.getId())) {
            throw new IllegalArgumentException("Đơn đã được giao cho shipper này.");
        }
        String previous = order.getShipper() == null ? "chưa phân công" : "ID " + order.getShipper().getId();
        order.setShipper(shipper); order.setUpdatedAt(LocalDateTime.now(clock));
        orders.saveAndFlush(order);
        audit.record(actor, "ORDER_ASSIGN", "order:" + orderId,
            previous + " -> shipper:" + shipper.getId() + "; " + request.getReason());
    }
}

