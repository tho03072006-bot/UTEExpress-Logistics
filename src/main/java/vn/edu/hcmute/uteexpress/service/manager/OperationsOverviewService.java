package vn.edu.hcmute.uteexpress.service.manager;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse;
public interface OperationsOverviewService {
    /** DROP_OFF_WAITING: đơn khách tự mang ra chi nhánh mà quá hạn lấy hàng vẫn chưa mang tới (không tính vào "Chậm lấy"). */
    enum Alert { UNASSIGNED, LATE_PICKUP, LATE_DELIVERY, RETRY_DUE, MANY_FAILURES, PAYMENT_ISSUE, DROP_OFF_WAITING }
    /** branchId null: Admin xem mọi chi nhánh, Manager xem chi nhánh được giao. */
    Page<Order> alerts(String actor, Alert alert, Long branchId, int page);
    default Page<Order> alerts(String actor, Alert alert, int page) { return alerts(actor, alert, null, page); }
    /** Số đơn của nhóm cảnh báo trong phạm vi chi nhánh, chỉ đếm (cho bảng hàng đợi). */
    long alertCount(String actor, Alert alert);
    /** Đơn nào trong danh sách là khách tự mang hàng ra chi nhánh. */
    java.util.Set<Long> dropOffOrderIds(java.util.Collection<Order> orders);
    java.util.Set<Long> deliveryHubOrderIds(String actor, java.util.Collection<Order> orders);
    java.util.List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor);
    Page<OrderComplaint> lateComplaints(String actor, Long branchId, int page);
    default Page<OrderComplaint> lateComplaints(String actor, int page) { return lateComplaints(actor, null, page); }
    long lateComplaintCount(String actor);
    Page<CodSettlement> lateCash(String actor, Long branchId, int page);
    default Page<CodSettlement> lateCash(String actor, int page) { return lateCash(actor, null, page); }
    /** Shipper của chi nhánh (Manager) hoặc chi nhánh Admin chọn; null là mọi chi nhánh với Admin. */
    Page<ShipperPerformanceResponse> performance(String actor, Long branchId, int page);
    default Page<ShipperPerformanceResponse> performance(String actor, int page) { return performance(actor, null, page); }
    /** Số giờ mỗi đơn tự mang đã chờ ở quầy, và hạn lấy hàng (giờ) để trang Kho đánh dấu đơn chờ quá lâu. */
    record DropOffWaiting(java.util.Map<Long, Long> hours, int limitHours) {
        public boolean overdue(Long orderId) { return hours.getOrDefault(orderId, 0L) >= limitHours; }
    }
    DropOffWaiting dropOffWaiting(java.util.Collection<vn.edu.hcmute.uteexpress.entity.routing.OrderRoute> dropOffs);
    /** Đơn nào trong danh sách đã chi trả xong bồi thường (hồ sơ hàng mất/hư hỏng đã đóng). */
    java.util.Set<Long> compensatedOrderIds(java.util.Collection<Long> orderIds);
}
