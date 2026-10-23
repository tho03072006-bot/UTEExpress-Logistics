package vn.edu.hcmute.uteexpress.service.routing;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.routing.Branch;
import vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsAccess;

/**
 * Phạm vi chi nhánh của nhân sự nội bộ cho các trang tra soát, cảnh báo, báo cáo.
 * null: Admin xem mọi chi nhánh; -1: Manager chưa được gán chi nhánh (không thấy đơn đã có tuyến nào).
 * Manager chọn chi nhánh khác chi nhánh được giao thì bị từ chối, giống trang phân công.
 */
@Service @Transactional(readOnly = true)
public class BranchScope {
    private final LogisticsAccess access;
    private final DispatchRoutingPolicy routing;
    private final OrderRouteRepository routes;
    private final vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository staff;
    public BranchScope(LogisticsAccess access, DispatchRoutingPolicy routing, OrderRouteRepository routes,
            vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository staff) {
        this.access = access; this.routing = routing; this.routes = routes; this.staff = staff;
    }
    public boolean isAdmin(String actor) { return access.internal(actor).getRole() == vn.edu.hcmute.uteexpress.entity.AppUser.Role.ADMIN; }
    public Long of(String actor, Long requested) { return routing.scope(access.internal(actor), requested); }
    /** Admin: cả 3 chi nhánh để chọn lọc; Manager: chỉ chi nhánh của mình. */
    public List<Branch> branches(String actor) { return routing.branches(access.internal(actor)); }
    /** ID các đơn nhân sự này được xem trong danh sách; Admin xem tất cả. */
    public Set<Long> visible(String actor, Collection<Order> orders) {
        var ids = orders.stream().map(Order::getId).filter(Objects::nonNull).toList();
        Long branch = of(actor, null);
        if (branch == null || ids.isEmpty()) return new HashSet<>(ids);
        return new HashSet<>(routes.idsInScope(ids, branch));
    }
    /** Chặn Manager mở đơn chỉ đi qua chi nhánh khác (đơn cũ chưa có tuyến vẫn mở được tới khi chuyển dữ liệu). */
    public void requireOrder(String actor, Order order) {
        if (order != null && !visible(actor, List.of(order)).contains(order.getId()))
            throw new AccessDeniedException("Đơn này không gửi đi hay giao tới chi nhánh của bạn.");
    }
    /** Phiếu nộp tiền thuộc chi nhánh của Shipper; Shipper chưa được gán chi nhánh thì mọi chi nhánh xem được (giống USER_SCOPE). */
    public void requireShipper(String actor, vn.edu.hcmute.uteexpress.entity.AppUser shipper) {
        Long branch = of(actor, null);
        if (branch == null || shipper == null) return;
        var assigned = staff.findByUser_Id(shipper.getId());
        if (assigned.isPresent() && !assigned.get().getBranch().getId().equals(branch))
            throw new AccessDeniedException("Phiếu này của Shipper chi nhánh khác.");
    }
}
