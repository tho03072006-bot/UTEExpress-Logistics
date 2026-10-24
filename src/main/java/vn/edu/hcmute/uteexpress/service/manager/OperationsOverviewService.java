package vn.edu.hcmute.uteexpress.service.manager;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse;
public interface OperationsOverviewService {
    enum Alert { UNASSIGNED, LATE_PICKUP, LATE_DELIVERY, RETRY_DUE, MANY_FAILURES, PAYMENT_ISSUE }
    /** branchId null: Admin xem mọi chi nhánh, Manager xem chi nhánh được giao. */
    Page<Order> alerts(String actor, Alert alert, Long branchId, int page);
    default Page<Order> alerts(String actor, Alert alert, int page) { return alerts(actor, alert, null, page); }
    /** Đơn nào trong danh sách là khách tự mang hàng ra chi nhánh. */
    java.util.Set<Long> dropOffOrderIds(java.util.Collection<Order> orders);
    java.util.Set<Long> deliveryHubOrderIds(String actor, java.util.Collection<Order> orders);
    java.util.List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor);
    Page<OrderComplaint> lateComplaints(String actor, int page);
    Page<CodSettlement> lateCash(String actor, int page);
    /** Shipper của chi nhánh (Manager) hoặc chi nhánh Admin chọn; null là mọi chi nhánh với Admin. */
    Page<ShipperPerformanceResponse> performance(String actor, Long branchId, int page);
    default Page<ShipperPerformanceResponse> performance(String actor, int page) { return performance(actor, null, page); }
}
