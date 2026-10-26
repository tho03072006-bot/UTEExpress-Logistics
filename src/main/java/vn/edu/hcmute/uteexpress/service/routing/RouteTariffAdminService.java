package vn.edu.hcmute.uteexpress.service.routing;

import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.RouteTariffUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.Order.ServiceType;
import vn.edu.hcmute.uteexpress.entity.routing.RouteTariff;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.routing.RouteTariffRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;

@Service @Transactional(readOnly = true)
public class RouteTariffAdminService {
    private final RouteTariffRepository tariffs;
    private final StaffAccessPolicy access;
    private final AuditService audit;
    public RouteTariffAdminService(RouteTariffRepository tariffs, StaffAccessPolicy access, AuditService audit) {
        this.tariffs = tariffs; this.access = access; this.audit = audit;
    }
    public List<RouteTariff> list(String actor) {
        access.requireAdmin(actor); return tariffs.findAll(Sort.by("originBranch.code", "destBranch.code", "serviceType"));
    }
    public RouteTariff get(String actor, Long id) {
        access.requireAdmin(actor); access.validateId(id);
        return tariffs.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biểu giá tuyến."));
    }
    @Transactional public void update(String actor, Long id, RouteTariffUpdateRequest form) {
        access.requireAdmin(actor); access.validateId(id); access.validate(form);
        var tariff = tariffs.lockById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biểu giá tuyến."));
        if (tariff.getRevision() != form.getRevision()) throw new StaffConflictException("Biểu giá vừa thay đổi. Hãy tải lại trang.");
        boolean local = Objects.equals(tariff.getOriginBranch().getId(), tariff.getDestBranch().getId());
        if (local && form.getSurcharge().signum() != 0) throw new IllegalArgumentException("Nội tỉnh chỉ tính phụ phí khoảng cách; phụ phí tuyến phải bằng 0.");
        if (!local && tariff.getServiceType() == ServiceType.SUPER_EXPRESS) throw new IllegalArgumentException("Không hỗ trợ Hỏa tốc liên tỉnh.");
        // Hạn giao và cảnh báo "Quá hạn giao" đọc số giờ từ đây (DeliveryDeadlines), nên chỉ nhận dạng đọc được.
        if (LeadTimes.hours(form.getLeadTimeText()).isEmpty())
            throw new IllegalArgumentException("Thời gian dự kiến phải có dạng " + LeadTimes.FORMAT_HINT + ", tối đa 30 ngày.");
        if (tariff.getSurcharge().compareTo(form.getSurcharge()) == 0 && tariff.getLeadTimeText().equals(form.getLeadTimeText()))
            throw new IllegalArgumentException("Chưa có thay đổi biểu giá.");
        String detail = tariff.getOriginBranch().getCode() + " -> " + tariff.getDestBranch().getCode() + "; "
                + tariff.getServiceType() + "; phụ phí " + tariff.getSurcharge() + " -> " + form.getSurcharge()
                + "; thời gian " + form.getLeadTimeText() + "; " + form.getReason();
        tariff.configure(form.getSurcharge(), form.getLeadTimeText()); tariffs.flush();
        audit.record(actor, "ROUTE_TARIFF_UPDATE", "route-tariff:" + id, detail);
    }
}
