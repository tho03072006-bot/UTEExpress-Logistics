package vn.edu.hcmute.uteexpress.service.routing;

import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.AuditEventRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsInput;

/** Điều kiện và xếp hạng dùng chung cho phân công thủ công lẫn hàng loạt. */
@Service @Transactional(readOnly = true)
public class DispatchRoutingPolicy {
    public static final List<Order.OrderStatus> ACTIVE = List.of(Order.OrderStatus.PENDING_PICKUP,
            Order.OrderStatus.PICKED_UP, Order.OrderStatus.IN_TRANSIT);
    private final StaffBranchRepository staff;
    private final ShipperZoneRepository zones;
    private final AreaNeighborRepository neighbors;
    private final OrderRouteRepository routes;
    private final OrderRepository orders;
    private final BranchRepository branches;
    private final OperationsPolicyService policies;
    private final AuditEventRepository audit;
    private final Clock clock;
    public DispatchRoutingPolicy(StaffBranchRepository staff, ShipperZoneRepository zones, AreaNeighborRepository neighbors,
            OrderRouteRepository routes, OrderRepository orders, BranchRepository branches, OperationsPolicyService policies,
            AuditEventRepository audit, Clock clock) {
        this.staff = staff; this.zones = zones; this.neighbors = neighbors; this.routes = routes; this.orders = orders;
        this.branches = branches; this.policies = policies; this.audit = audit; this.clock = clock;
    }
    public record Candidate(Long id, String username, String fullName, long activeOrders, int capacity,
                            long assignedToday, int rank, String areas, String reason) { }
    /** -1 dành cho Manager chưa được cấu hình; null chỉ dành cho Admin xem mọi chi nhánh. */
    public Long scope(AppUser actor, Long requested) {
        if (requested != null && requested <= 0) throw new IllegalArgumentException("Mã chi nhánh không hợp lệ.");
        if (actor.getRole() == AppUser.Role.ADMIN) {
            if (requested != null && !branches.existsById(requested)) throw new IllegalArgumentException("Không tìm thấy chi nhánh.");
            return requested;
        }
        Long assigned = staff.findByUser_Id(actor.getId()).map(s -> s.getBranch().getId()).orElse(-1L);
        if (requested != null && !requested.equals(assigned)) throw new AccessDeniedException("Bạn chỉ được điều phối chi nhánh được giao.");
        return assigned;
    }
    public List<Branch> branches(AppUser actor) {
        Long id = scope(actor, null);
        return id == null ? branches.findAll(Sort.by("code")) : branches.findById(id).map(List::of).orElse(List.of());
    }
    public Page<Order> pending(AppUser actor, Long branchId, int page) {
        return routes.pending(scope(actor, branchId), PageRequest.of(LogisticsInput.page(page), 10));
    }
    public List<String> unassigned(AppUser actor, Long branchId) { return routes.unassignedCodes(scope(actor, branchId)); }
    public OrderRoute route(Order order) {
        return routes.findByOrder_Id(order.getId()).orElseThrow(() ->
                new IllegalArgumentException("Đơn cũ chưa có khu vực và tuyến. Cần chuyển dữ liệu trước khi phân công."));
    }
    public Branch branch(OrderRoute route) { return route.getStage() == OrderRoute.Stage.PICKUP ? route.getOriginBranch() : route.getDestBranch(); }
    public void requireStage(OrderRoute route) {
        Order order = route.getOrder();
        boolean pickup = route.getStage() == OrderRoute.Stage.PICKUP && order.getStatus() == Order.OrderStatus.PENDING_PICKUP;
        boolean delivery = route.getStage() == OrderRoute.Stage.DEST_HUB && order.getStatus() == Order.OrderStatus.PICKED_UP;
        if (!pickup && !delivery) throw new IllegalArgumentException("Chỉ phân công đơn chờ lấy hoặc chờ giao tại kho nhận.");
    }
    public void requireManager(AppUser actor, OrderRoute route) {
        Long id = scope(actor, null);
        if (id != null && !id.equals(branch(route).getId())) throw new AccessDeniedException("Bạn không được phân công chặng ở chi nhánh khác.");
    }
    public long load(Long shipperId) { return orders.countByShipper_IdAndStatusIn(shipperId, ACTIVE); }
    public void requireShipper(AppUser shipper, OrderRoute route) {
        if (!shipper.isEnabled() || shipper.isLockedByAdmin() || shipper.getRole() != AppUser.Role.SHIPPER)
            throw new IllegalArgumentException("Hãy chọn Shipper đang hoạt động.");
        Long branchId = staff.findByUser_Id(shipper.getId()).map(s -> s.getBranch().getId()).orElse(null);
        if (!branch(route).getId().equals(branchId)) throw new IllegalArgumentException("Shipper phải thuộc đúng chi nhánh của chặng.");
        if (load(shipper.getId()) >= policies.current().getMaxActiveOrders()) throw new IllegalArgumentException("Shipper đã đủ số đơn tối đa đang giữ.");
    }
    public List<AppUser> branchShippers(Long branchId) {
        return staff.findAllByBranch_Id(branchId).stream().map(StaffBranch::getUser)
                .filter(u -> u.getRole() == AppUser.Role.SHIPPER).sorted(Comparator.comparing(AppUser::getId)).toList();
    }
    public List<Candidate> candidates(AppUser actor, Order order) {
        var route = route(order); requireStage(route); requireManager(actor, route);
        var area = route.getStage() == OrderRoute.Stage.PICKUP ? route.getPickupArea() : route.getDeliveryArea();
        var adjacent = new HashSet<String>();
        neighbors.findAllById_AreaCode(area.getAreaCode()).forEach(n -> adjacent.add(n.getNeighbor().getAreaCode()));
        int cap = policies.current().getMaxActiveOrders(); var today = LocalDate.now(clock).atStartOfDay();
        var result = new ArrayList<Candidate>();
        for (var user : branchShippers(branch(route).getId())) {
            if (!user.isEnabled() || user.isLockedByAdmin() || (order.getShipper() != null && user.getId().equals(order.getShipper().getId()))) continue;
            long load = load(user.getId()); if (load >= cap) continue;
            var assigned = zones.findAllByShipper_Id(user.getId()).stream()
                    .filter(z -> z.getArea().getBranch().getId().equals(branch(route).getId())).toList();
            int rank = rank(area.getAreaCode(), adjacent, assigned);
            String label = switch (rank) { case 0 -> "khu vực chính"; case 1 -> "khu vực phụ"; case 2 -> "khu vực lân cận"; default -> "cùng chi nhánh"; };
            String zoneText = assigned.stream().sorted(Comparator.comparing(ShipperZone::isPrimary).reversed())
                    .map(z -> z.getArea().getAreaName() + (z.isPrimary() ? " (chính)" : " (phụ)")).collect(java.util.stream.Collectors.joining(", "));
            long daily = audit.countByActionAndDetailStartingWithAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("ORDER_ASSIGN", "Shipper " + user.getId() + "; ", today, today.plusDays(1));
            result.add(new Candidate(user.getId(), user.getUsername(), user.getFullName(), load, cap, daily, rank, zoneText,
                    "Gợi ý hệ thống: " + area.getAreaName() + ", " + label + ", đang giữ " + load + "/" + cap + " đơn."));
        }
        result.sort(Comparator.comparingInt(Candidate::rank).thenComparingLong(Candidate::activeOrders)
                .thenComparingLong(Candidate::assignedToday).thenComparing(Candidate::id));
        return result;
    }
    static int rank(String code, Set<String> adjacent, List<ShipperZone> zones) {
        return zones.stream().mapToInt(z -> z.getArea().getAreaCode().equals(code) ? (z.isPrimary() ? 0 : 1)
                : adjacent.contains(z.getArea().getAreaCode()) ? 2 : 3).min().orElse(3);
    }
}
