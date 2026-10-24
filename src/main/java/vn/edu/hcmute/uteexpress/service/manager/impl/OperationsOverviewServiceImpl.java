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
    private final OperationsOverviewRepository data;
    private final OperationsPolicyService policies;
    private final StaffAccessPolicy access;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.service.routing.BranchScope scope;
    private final vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes;
    public OperationsOverviewServiceImpl(OperationsOverviewRepository data, OperationsPolicyService policies, StaffAccessPolicy access, Clock clock,
            vn.edu.hcmute.uteexpress.service.routing.BranchScope scope, vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes) {
        this.data = data; this.policies = policies; this.access = access; this.clock = clock; this.scope = scope; this.routes = routes;
    }
    public Page<Order> alerts(String actor, Alert alert, Long branchId, int page) {
        access.requireOperator(actor); access.validatePage(page);
        if (alert == null) throw new IllegalArgumentException("Chọn nhóm cảnh báo hợp lệ.");
        Long branch = scope.of(actor, branchId);
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        return data.alerts(alert.name(), branch, now.minusHours(policy.getPickupHours()), now.minusHours(policy.getStandardHours()),
                now.minusHours(policy.getExpressHours()), now.minusHours(policy.getSuperExpressHours()), now,
                policy.getMaxDeliveryAttempts(), PageRequest.of(page, 20, Sort.by("createdAt", "id")));
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
    public Page<OrderComplaint> lateComplaints(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page);
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        return data.lateComplaints(now.minusHours(policy.getComplaintResponseHours()), now.minusHours(policy.getComplaintResolveHours()),
                scope.of(actor, null), PageRequest.of(page, 10, Sort.by("createdAt", "id")));
    }
    public Page<ShipperPerformanceResponse> performance(String actor, Long branchId, int page) {
        access.requireOperator(actor); access.validatePage(page); return data.performance(scope.of(actor, branchId), PageRequest.of(page, 20));
    }
    public Page<CodSettlement> lateCash(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page);
        return data.lateCash(LocalDateTime.now(clock).minusDays(7), scope.of(actor, null), PageRequest.of(page, 10, Sort.by("reviewedAt", "id")));
    }
}
