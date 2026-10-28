package vn.edu.hcmute.uteexpress.service.manager.impl;
import java.time.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse;
import vn.edu.hcmute.uteexpress.repository.manager.OperationsOverviewRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService;
@Service @Transactional(readOnly = true)
public class OperationsOverviewServiceImpl implements OperationsOverviewService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    /** Câu đếm gộp theo từng tổ hợp nhóm cảnh báo, dựng một lần rồi dùng lại. */
    private static final java.util.Map<java.util.List<Alert>, String> COUNT_QUERIES = new java.util.concurrent.ConcurrentHashMap<>();
    private final OperationsOverviewRepository data;
    private final OperationsPolicyService policies;
    private final StaffAccessPolicy access;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.service.routing.BranchScope scope;
    private final vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes;
    private final vn.edu.hcmute.uteexpress.service.routing.DeliveryDeadlines deadlines;
    private final vn.edu.hcmute.uteexpress.repository.routing.LinehaulItemRepository linehaulItems;
    private final vn.edu.hcmute.uteexpress.repository.admin.OperationsCaseRepository cases;
    public OperationsOverviewServiceImpl(OperationsOverviewRepository data, OperationsPolicyService policies, StaffAccessPolicy access, Clock clock,
            vn.edu.hcmute.uteexpress.service.routing.BranchScope scope, vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes,
            vn.edu.hcmute.uteexpress.service.routing.DeliveryDeadlines deadlines,
            vn.edu.hcmute.uteexpress.repository.routing.LinehaulItemRepository linehaulItems,
            vn.edu.hcmute.uteexpress.repository.admin.OperationsCaseRepository cases) {
        this.data = data; this.policies = policies; this.access = access; this.clock = clock; this.scope = scope; this.routes = routes;
        this.deadlines = deadlines; this.linehaulItems = linehaulItems; this.cases = cases;
    }
    public Page<Order> alerts(String actor, Alert alert, Long branchId, int page) {
        access.requireOperator(actor); access.validatePage(page);
        if (alert == null) throw new IllegalArgumentException("Chọn nhóm cảnh báo hợp lệ.");
        Long branch = scope.of(actor, branchId);
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        var pageable = PageRequest.of(page, 20, Sort.by("createdAt", "id"));
        if (alert == Alert.LATE_DELIVERY) return lateDeliveries(branch, policy, now, pageable);
        return data.alerts(alert.name(), branch, now.minusHours(policy.getPickupHours()),
                now.minusHours(deadlines.rules(policy).superExpressPickupHours()), now, policy.getMaxDeliveryAttempts(), pageable);
    }
    /**
     * Quá hạn giao theo hạn của từng tuyến, tính từ lúc lấy hàng; số đơn đang trên đường giao của một chi nhánh nhỏ nên lọc
     * và phân trang tại đây. Không tính kiện đang ghi thiếu trên chuyến đã tới (theo dõi ở mục "Kiện ghi thiếu" của trang Kho)
     * và đơn đã chi trả xong bồi thường (hồ sơ hàng mất/hư hỏng đã đóng), để cảnh báo không giữ mãi những đơn đã có người xử lý.
     */
    private Page<Order> lateDeliveries(Long branch, vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy policy, LocalDateTime now, Pageable pageable) {
        var rules = deadlines.rules(policy);
        var candidates = data.deliveryCandidates(now.minusHours(rules.shortest()), branch);
        var ids = candidates.stream().map(Order::getId).toList();
        java.util.Map<Long, vn.edu.hcmute.uteexpress.entity.routing.OrderRoute> routeByOrder = new java.util.HashMap<>();
        java.util.Set<Long> closed = new java.util.HashSet<>();
        if (!ids.isEmpty()) {
            for (var route : routes.findWithAreasByOrderIds(ids)) routeByOrder.put(route.getOrder().getId(), route);
            closed.addAll(linehaulItems.missingOrderIds(ids)); closed.addAll(cases.compensatedOrderIds(ids));
        }
        var pickedUp = deadlines.pickupTimes(ids);
        var late = candidates.stream().filter(order -> !closed.contains(order.getId()))
                .filter(order -> !rules.due(order, routeByOrder.get(order.getId()), pickedUp.get(order.getId())).isAfter(now)).toList();
        int from = (int) Math.min(pageable.getOffset(), late.size());
        return new PageImpl<>(late.subList(from, Math.min(from + pageable.getPageSize(), late.size())), pageable, late.size());
    }
    public DropOffWaiting dropOffWaiting(java.util.Collection<vn.edu.hcmute.uteexpress.entity.routing.OrderRoute> dropOffs) {
        var now = LocalDateTime.now(clock);
        java.util.Map<Long, Long> hours = new java.util.HashMap<>();
        for (var route : dropOffs) if (route.getOrder().getCreatedAt() != null)
            hours.put(route.getOrder().getId(), Math.max(0, Duration.between(route.getOrder().getCreatedAt(), now).toHours()));
        return new DropOffWaiting(java.util.Map.copyOf(hours), policies.current().getPickupHours());
    }
    public java.util.Set<Long> compensatedOrderIds(java.util.Collection<Long> orderIds) {
        return orderIds.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>(cases.compensatedOrderIds(orderIds));
    }
    public java.util.Set<Long> dropOffOrderIds(java.util.Collection<Order> orders) {
        var ids = orders.stream().map(Order::getId).toList();
        return ids.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>(routes.dropOffOrderIds(ids));
    }
    public java.util.List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor) { return scope.branches(actor); }
    public java.util.Set<Long> deliveryHubOrderIds(String actor, java.util.Collection<Order> orders) {
        access.requireOperator(actor);
        Long branch = scope.of(actor, null);
        var ids = orders.stream().filter(o -> o.getStatus() == Order.OrderStatus.FAILED).map(Order::getId).toList();
        return ids.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>(routes.idsAtDeliveryHub(ids, branch));
    }
    public long alertCount(String actor, Alert alert) {
        access.requireOperator(actor);
        if (alert == null) throw new IllegalArgumentException("Chọn nhóm cảnh báo hợp lệ.");
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        Long branch = scope.of(actor, null);
        // "Quá hạn giao" đếm theo hạn từng tuyến như trang danh sách, để số trên bảng hàng đợi khớp số đơn khi mở ra.
        if (alert == Alert.LATE_DELIVERY) return lateDeliveries(branch, policy, now, PageRequest.of(0, 1)).getTotalElements();
        return data.countAlerts(alert.name(), branch, now.minusHours(policy.getPickupHours()),
                now.minusHours(deadlines.rules(policy).superExpressPickupHours()), now, policy.getMaxDeliveryAttempts());
    }
    public java.util.Map<Alert, Long> alertCounts(String actor, java.util.Collection<Alert> alerts) {
        access.requireOperator(actor);
        if (alerts == null || alerts.isEmpty() || alerts.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("Chọn nhóm cảnh báo hợp lệ.");
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        Long branch = scope.of(actor, null);
        var result = new java.util.EnumMap<Alert, Long>(Alert.class);
        if (alerts.contains(Alert.LATE_DELIVERY))
            result.put(Alert.LATE_DELIVERY, lateDeliveries(branch, policy, now, PageRequest.of(0, 1)).getTotalElements());
        var grouped = alerts.stream().filter(alert -> alert != Alert.LATE_DELIVERY).distinct().sorted().toList();
        if (grouped.isEmpty()) return result;
        // Một câu SQL đếm mọi nhóm (mỗi nhóm một cột sum(case ...)) thay vì một câu mỗi nhóm; cùng điều kiện ALERT_FILTER
        // như trang danh sách nên số đếm khớp. Mã nhóm là hằng enum ghép vào câu, không lấy từ dữ liệu người dùng.
        String jpql = COUNT_QUERIES.computeIfAbsent(grouped, kinds -> "select " + kinds.stream()
                .map(kind -> "sum(case when (" + OperationsOverviewRepository.ALERT_FILTER.replace(":kind", "'" + kind.name() + "'") + ") then 1 else 0 end)")
                .collect(java.util.stream.Collectors.joining(", ")) + " from Order o");
        var query = entityManager.createQuery(jpql)
                .setParameter("branchId", branch).setParameter("pickup", now.minusHours(policy.getPickupHours()))
                .setParameter("superExpressPickup", now.minusHours(deadlines.rules(policy).superExpressPickupHours()))
                .setParameter("now", now).setParameter("maxAttempts", policy.getMaxDeliveryAttempts());
        Object single = query.getSingleResult();
        Object[] row = single instanceof Object[] values ? values : new Object[] { single };
        for (int i = 0; i < grouped.size(); i++) result.put(grouped.get(i), row[i] == null ? 0L : ((Number) row[i]).longValue());
        return result;
    }
    public long lateComplaintCount(String actor) {
        access.requireOperator(actor);
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        return data.countLateComplaints(now.minusHours(policy.getComplaintResponseHours()), now.minusHours(policy.getComplaintResolveHours()), scope.of(actor, null));
    }
    public Page<OrderComplaint> lateComplaints(String actor, Long branchId, int page) {
        access.requireOperator(actor); access.validatePage(page);
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        return data.lateComplaints(now.minusHours(policy.getComplaintResponseHours()), now.minusHours(policy.getComplaintResolveHours()),
                scope.of(actor, branchId), PageRequest.of(page, 10, Sort.by("createdAt", "id")));
    }
    public Page<ShipperPerformanceResponse> performance(String actor, Long branchId, int page) {
        access.requireOperator(actor); access.validatePage(page); return data.performance(scope.of(actor, branchId), PageRequest.of(page, 20));
    }
    public Page<CodSettlement> lateCash(String actor, Long branchId, int page) {
        access.requireOperator(actor); access.validatePage(page);
        return data.lateCash(LocalDateTime.now(clock).minusDays(7), scope.of(actor, branchId), PageRequest.of(page, 10, Sort.by("reviewedAt", "id")));
    }
}
