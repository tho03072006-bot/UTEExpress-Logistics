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
    public OperationsOverviewServiceImpl(OperationsOverviewRepository data, OperationsPolicyService policies, StaffAccessPolicy access, Clock clock) {
        this.data = data; this.policies = policies; this.access = access; this.clock = clock;
    }
    public Page<Order> alerts(String actor, Alert alert, int page) {
        access.requireOperator(actor); access.validatePage(page);
        if (alert == null) throw new IllegalArgumentException("Chọn nhóm cảnh báo hợp lệ.");
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        return data.alerts(alert.name(), now.minusHours(policy.getPickupHours()), now.minusHours(policy.getStandardHours()),
                now.minusHours(policy.getExpressHours()), now.minusHours(policy.getSuperExpressHours()), now,
                policy.getMaxDeliveryAttempts(), PageRequest.of(page, 20, Sort.by("createdAt", "id")));
    }
    public Page<OrderComplaint> lateComplaints(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page);
        var policy = policies.current(); var now = LocalDateTime.now(clock);
        return data.lateComplaints(now.minusHours(policy.getComplaintResponseHours()), now.minusHours(policy.getComplaintResolveHours()),
                PageRequest.of(page, 10, Sort.by("createdAt", "id")));
    }
    public Page<ShipperPerformanceResponse> performance(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page); return data.performance(PageRequest.of(page, 20));
    }
    public Page<CodSettlement> lateCash(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page);
        return data.lateCash(LocalDateTime.now(clock).minusDays(7), PageRequest.of(page, 10, Sort.by("reviewedAt", "id")));
    }
}
