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
        Long assigned = vn.edu.hcmute.uteexpress.service.tracking.LogisticsAccess.findBranch(staff, actor.getId()).map(s -> s.getBranch().getId()).orElse(-1L);
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
        boolean pickup = route.getStage() == OrderRoute.Stage.PICKUP && !route.isDropOff()
                && order.getStatus() == Order.OrderStatus.PENDING_PICKUP;
        boolean delivery = route.getStage() == OrderRoute.Stage.DEST_HUB && order.getStatus() == Order.OrderStatus.PICKED_UP;
        if (!pickup && !delivery) throw new IllegalArgumentException("Chỉ phân công đơn chờ lấy hoặc chờ giao tại kho nhận.");
    }
    public void requireManager(AppUser actor, OrderRoute route) { requireManager(scope(actor, null), route); }
    private void requireManager(Long scope, OrderRoute route) {
        if (scope != null && !scope.equals(branch(route).getId())) throw new AccessDeniedException("Bạn không được phân công chặng ở chi nhánh khác.");
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
        return staff.findWithUserByBranchIds(List.of(branchId)).stream().map(StaffBranch::getUser)
                .filter(u -> u.getRole() == AppUser.Role.SHIPPER).sorted(Comparator.comparing(AppUser::getId)).toList();
    }
    public List<Candidate> candidates(AppUser actor, Order order) { return candidates(actor, List.of(order)).get(order.getId()); }
    /**
     * Gợi ý cho cả trang đơn: tuyến, shipper, số đơn đang giữ, khu vực và lượt phân công hôm nay đều đọc theo lô
     * (vài truy vấn cho cả trang thay vì mỗi đơn × mỗi shipper ba truy vấn). Thứ tự xếp hạng như khi xét từng đơn.
     */
    public Map<Long, List<Candidate>> candidates(AppUser actor, List<Order> list) {
        var result = new LinkedHashMap<Long, List<Candidate>>();
        if (list.isEmpty()) return result;
        var routeByOrder = new HashMap<Long, OrderRoute>();
        routes.findWithAreasByOrderIds(list.stream().map(Order::getId).toList()).forEach(r -> routeByOrder.put(r.getOrder().getId(), r));
        Long scope = scope(actor, null);
        for (var order : list) {
            var route = routeByOrder.get(order.getId());
            if (route == null) throw new IllegalArgumentException("Đơn cũ chưa có khu vực và tuyến. Cần chuyển dữ liệu trước khi phân công.");
            requireStage(route); requireManager(scope, route);
        }
        var shippersByBranch = new HashMap<Long, List<AppUser>>();
        staff.findWithUserByBranchIds(routeByOrder.values().stream().map(r -> branch(r).getId()).collect(java.util.stream.Collectors.toSet()))
                .stream().filter(s -> s.getUser().getRole() == AppUser.Role.SHIPPER)
                .forEach(s -> shippersByBranch.computeIfAbsent(s.getBranch().getId(), k -> new ArrayList<>()).add(s.getUser()));
        shippersByBranch.values().forEach(users -> users.sort(Comparator.comparing(AppUser::getId)));
        var shipperIds = shippersByBranch.values().stream().flatMap(List::stream).map(AppUser::getId).toList();
        var loads = new HashMap<Long, Long>(); var zonesByShipper = new HashMap<Long, List<ShipperZone>>();
        if (!shipperIds.isEmpty()) {
            orders.countByShipperIds(shipperIds, ACTIVE).forEach(row -> loads.put((Long) row[0], (Long) row[1]));
            zones.findWithAreaByShipperIds(shipperIds).stream().sorted(Comparator.comparing(ShipperZone::getId))
                    .forEach(z -> zonesByShipper.computeIfAbsent(z.getShipper().getId(), k -> new ArrayList<>()).add(z));
        }
        var adjacentByArea = new HashMap<String, Set<String>>();
        neighbors.neighborCodes(routeByOrder.values().stream().map(r -> area(r).getAreaCode()).collect(java.util.stream.Collectors.toSet()))
                .forEach(row -> adjacentByArea.computeIfAbsent((String) row[0], k -> new HashSet<>()).add((String) row[1]));
        int cap = policies.current().getMaxActiveOrders(); var today = LocalDate.now(clock).atStartOfDay();
        var assignedToday = assignedToday(audit.detailsBetween("ORDER_ASSIGN", today, today.plusDays(1)));
        for (var order : list) {
            var route = routeByOrder.get(order.getId()); var area = area(route); Long branchId = branch(route).getId();
            var adjacent = adjacentByArea.getOrDefault(area.getAreaCode(), Set.of());
            var candidates = new ArrayList<Candidate>();
            for (var user : shippersByBranch.getOrDefault(branchId, List.of())) {
                if (!user.isEnabled() || user.isLockedByAdmin() || (order.getShipper() != null && user.getId().equals(order.getShipper().getId()))) continue;
                long load = loads.getOrDefault(user.getId(), 0L); if (load >= cap) continue;
                var assigned = zonesByShipper.getOrDefault(user.getId(), List.of()).stream()
                        .filter(z -> z.getArea().getBranch().getId().equals(branchId)).toList();
                int rank = rank(area.getAreaCode(), adjacent, assigned);
                String label = switch (rank) { case 0 -> "khu vực chính"; case 1 -> "khu vực phụ"; case 2 -> "khu vực lân cận"; default -> "cùng chi nhánh"; };
                String zoneText = assigned.stream().sorted(Comparator.comparing(ShipperZone::isPrimary).reversed())
                        .map(z -> z.getArea().getAreaName() + (z.isPrimary() ? " (chính)" : " (phụ)")).collect(java.util.stream.Collectors.joining(", "));
                candidates.add(new Candidate(user.getId(), user.getUsername(), user.getFullName(), load, cap,
                        assignedToday.getOrDefault(String.valueOf(user.getId()), 0L), rank, zoneText,
                        "Gợi ý hệ thống: " + area.getAreaName() + ", " + label + ", đang giữ " + load + "/" + cap + " đơn."));
            }
            candidates.sort(Comparator.comparingInt(Candidate::rank).thenComparingLong(Candidate::activeOrders)
                    .thenComparingLong(Candidate::assignedToday).thenComparing(Candidate::id));
            result.put(order.getId(), candidates);
        }
        return result;
    }
    private static ProvinceArea area(OrderRoute route) {
        return route.getStage() == OrderRoute.Stage.PICKUP ? route.getPickupArea() : route.getDeliveryArea();
    }
    /** Đếm lượt phân công theo shipper từ chi tiết nhật ký dạng "Shipper {id}; {lý do}". */
    static Map<String, Long> assignedToday(List<String> details) {
        var counts = new HashMap<String, Long>();
        for (var detail : details) {
            int end = detail == null || !detail.startsWith("Shipper ") ? -1 : detail.indexOf("; ");
            if (end > 8) counts.merge(detail.substring(8, end), 1L, Long::sum);
        }
        return counts;
    }
    static int rank(String code, Set<String> adjacent, List<ShipperZone> zones) {
        return zones.stream().mapToInt(z -> z.getArea().getAreaCode().equals(code) ? (z.isPrimary() ? 0 : 1)
                : adjacent.contains(z.getArea().getAreaCode()) ? 2 : 3).min().orElse(3);
    }
}
