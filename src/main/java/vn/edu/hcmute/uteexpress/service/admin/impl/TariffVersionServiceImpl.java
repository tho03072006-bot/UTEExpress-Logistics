package vn.edu.hcmute.uteexpress.service.admin.impl;
import java.time.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.TariffScheduleRequest;
import vn.edu.hcmute.uteexpress.entity.admin.ShippingTariffVersion;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsInput;
@Service @Transactional(readOnly = true)
public class TariffVersionServiceImpl implements TariffVersionService {
    private final ShippingTariffVersionRepository versions;
    private final ShippingTariffRepository tariffs;
    private final StaffAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;
    public TariffVersionServiceImpl(ShippingTariffVersionRepository versions, ShippingTariffRepository tariffs,
            StaffAccessPolicy access, AuditService audit, Clock clock) {
        this.versions = versions; this.tariffs = tariffs; this.access = access; this.audit = audit; this.clock = clock;
    }
    public Page<ShippingTariffVersion> list(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page);
        return versions.findAllByOrderByStartsAtDescIdDesc(PageRequest.of(page, 20));
    }
    // Sau khi chờ khóa cước gốc, phải thấy lịch request trước vừa commit, không dùng read view cũ của InnoDB.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void schedule(String actor, TariffScheduleRequest form) {
        access.requireAdmin(actor); access.validate(form);
        String reason = LogisticsInput.text(form.reason(), 5, 500, "Lý do");
        LocalDateTime now = LocalDateTime.now(clock);
        if (form.startsAt().getSecond() != 0 || form.startsAt().getNano() != 0
                || form.endsAt().getSecond() != 0 || form.endsAt().getNano() != 0)
            throw new IllegalArgumentException("Thời gian lịch cước phải theo phút, không nhập phần giây hoặc phần thập phân.");
        if (form.startsAt().isBefore(now.plusMinutes(1)) || !form.endsAt().isAfter(form.startsAt())
                || form.endsAt().isAfter(now.plusYears(2)) || form.baseFee().signum() + form.feePerKg().signum() == 0)
            throw new IllegalArgumentException("Giá không đồng thời bằng 0; lịch bắt đầu sau ít nhất 1 phút, kết thúc sau bắt đầu và trong 2 năm.");
        tariffs.lockByServiceType(form.serviceType()).orElseThrow(() -> new IllegalArgumentException("Dịch vụ chưa có cước gốc."));
        if (versions.overlaps(form.serviceType(), form.startsAt(), form.endsAt()) > 0)
            throw new StaffConflictException("Lịch cước bị trùng với phiên bản chưa hủy.");
        var version = versions.saveAndFlush(new ShippingTariffVersion(form.serviceType(), form.baseFee(), form.feePerKg(),
                form.startsAt(), form.endsAt(), actor, reason, now));
        audit.record(actor, "TARIFF_SCHEDULE", "tariff-version:" + version.getId(), reason);
    }
    @Transactional public void cancel(String actor, Long id, long revision, String reason) {
        access.requireAdmin(actor); access.validateId(id);
        String note = LogisticsInput.text(reason, 5, 500, "Lý do");
        var version = versions.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch cước."));
        tariffs.lockByServiceType(version.getServiceType()).orElseThrow();
        if (revision < 0 || version.getRevision() != revision || version.isCancelled()
                || !version.getStartsAt().isAfter(LocalDateTime.now(clock)))
            throw new StaffConflictException("Chỉ hủy lịch chưa bắt đầu và chưa thay đổi. Không sửa lịch sử giá.");
        version.cancel(); versions.saveAndFlush(version);
        audit.record(actor, "TARIFF_CANCEL", "tariff-version:" + id, note);
    }
}
