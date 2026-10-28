package vn.edu.hcmute.uteexpress.service.admin.impl;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.OperationsPolicyRequest;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.OperationsPolicyRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsInput;
@Service @Transactional(readOnly = true)
public class OperationsPolicyServiceImpl implements OperationsPolicyService {
    private final OperationsPolicyRepository policies;
    private final StaffAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;
    public OperationsPolicyServiceImpl(OperationsPolicyRepository policies, StaffAccessPolicy access, AuditService audit, Clock clock) {
        this.policies = policies; this.access = access; this.audit = audit; this.clock = clock;
    }
    public OperationsPolicy current() {
        return policies.findById(1L).orElseThrow(() -> new IllegalStateException("Chưa nâng cấp chính sách vận hành. Liên hệ Admin."));
    }
    @Transactional public void update(String actor, OperationsPolicyRequest form) {
        access.requireAdmin(actor); access.validate(form);
        String reason = LogisticsInput.text(form.reason(), 5, 500, "Lý do");
        if (form.superExpressHours() > form.expressHours() || form.expressHours() > form.standardHours()
                || form.complaintResponseHours() > form.complaintResolveHours())
            throw new IllegalArgumentException("Hỏa tốc không được chậm hơn Nhanh/Tiêu chuẩn; hạn phản hồi không được sau hạn giải quyết.");
        var policy = policies.lockPolicy().orElseThrow(() -> new IllegalStateException("Chưa có chính sách vận hành."));
        if (policy.getRevision() != form.revision()) throw new StaffConflictException("Chính sách đã thay đổi. Hãy tải lại trang.");
        policy.configure(form.pickupHours(), form.standardHours(), form.expressHours(), form.superExpressHours(),
                form.maxDeliveryAttempts(), form.complaintResponseHours(), form.complaintResolveHours(), actor, LocalDateTime.now(clock));
        policy.setMaxActiveOrders(form.maxActiveOrders());
        var oldDiscount = policy.getDropOffDiscount();
        var newDiscount = java.math.BigDecimal.valueOf(form.dropOffDiscount());
        policy.setDropOffDiscount(newDiscount);
        policies.saveAndFlush(policy);
        String detail = oldDiscount.compareTo(newDiscount) == 0 ? reason
                : reason + "; Giảm khi tự mang ra chi nhánh: " + oldDiscount.toPlainString() + " → " + newDiscount.toPlainString() + " đ";
        audit.record(actor, "POLICY_UPDATE", "operations-policy:1", detail.length() > 500 ? detail.substring(0, 500) : detail);
    }
}
