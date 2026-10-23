package vn.edu.hcmute.uteexpress.service.admin.impl;

import java.math.*;
import java.time.*;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.ShippingTariffUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.ShippingTariff;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.ShippingTariffRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;

@Service @Transactional
public class ShippingTariffServiceImpl implements ShippingTariffService {
    private final ShippingTariffRepository tariffs;
    private final StaffAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.repository.admin.ShippingTariffVersionRepository versions;
    public ShippingTariffServiceImpl(ShippingTariffRepository tariffs, StaffAccessPolicy access, AuditService audit, Clock clock,
            vn.edu.hcmute.uteexpress.repository.admin.ShippingTariffVersionRepository versions) {
        this.tariffs = tariffs; this.access = access; this.audit = audit; this.clock = clock;
        this.versions = versions;
    }
    @Override @Transactional(readOnly = true)
    public BigDecimal quote(Order.ServiceType serviceType, double weightKg) {
        if (serviceType == null) throw new IllegalArgumentException("Vui lòng chọn dịch vụ.");
        vn.edu.hcmute.uteexpress.validation.ShippingInputValidation.requireWeight(weightKg);
        var tariff = currentTariff(serviceType);
        return tariff.baseFee().add(tariff.feePerKg().multiply(BigDecimal.valueOf(weightKg)))
            .setScale(0, RoundingMode.HALF_UP);
    }
    @Override @Transactional(readOnly = true)
    public List<vn.edu.hcmute.uteexpress.dto.CurrentTariffResponse> currentTariffs() {
        return java.util.Arrays.stream(Order.ServiceType.values()).map(this::currentTariff).toList();
    }
    private vn.edu.hcmute.uteexpress.dto.CurrentTariffResponse currentTariff(Order.ServiceType serviceType) {
        ShippingTariff tariff = tariffs.findByServiceType(serviceType)
            .orElseThrow(() -> new IllegalStateException("Chưa cấu hình cước cho dịch vụ này. Liên hệ Admin."));
        var active = versions.active(serviceType, LocalDateTime.now(clock));
        if (active.size() > 1) throw new IllegalStateException("Lịch cước bị trùng. Liên hệ Admin, không tự chọn giá.");
        BigDecimal base = active.isEmpty() ? tariff.getBaseFee() : active.get(0).getBaseFee();
        BigDecimal perKg = active.isEmpty() ? tariff.getFeePerKg() : active.get(0).getFeePerKg();
        return new vn.edu.hcmute.uteexpress.dto.CurrentTariffResponse(serviceType,base,perKg);
    }
    @Override @Transactional(readOnly = true)
    public List<vn.edu.hcmute.uteexpress.dto.admin.ShippingTariffResponse> findTariffs(String actor) {
        access.requireOperator(actor);
        return tariffs.findAll(Sort.by("serviceType")).stream()
            .map(vn.edu.hcmute.uteexpress.dto.admin.ShippingTariffResponse::from).toList();
    }
    @Override
    public void updateTariff(String actor, Order.ServiceType serviceType, ShippingTariffUpdateRequest request) {
        access.requireAdmin(actor); access.validate(request);
        if (serviceType == null) { throw new IllegalArgumentException("Thiếu dịch vụ."); }
        if (request.getBaseFee().signum() == 0 && request.getFeePerKg().signum() == 0) {
            throw new IllegalArgumentException("Không được đặt đồng thời cả hai mức phí bằng 0.");
        }
        var tariff = tariffs.lockByServiceType(serviceType)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng cước."));
        if (!versions.active(serviceType, LocalDateTime.now(clock)).isEmpty())
            throw new IllegalArgumentException("Đang có phiên bản cước hiệu lực; không sửa giá gốc lúc này.");
        if (tariff.getRevision() != request.getRevision()) {
            throw new StaffConflictException("Bảng cước vừa thay đổi. Hãy tải lại trang.");
        }
        if (tariff.getBaseFee().compareTo(request.getBaseFee()) == 0 && tariff.getFeePerKg().compareTo(request.getFeePerKg()) == 0) {
            throw new IllegalArgumentException("Chưa có thay đổi mức phí.");
        }
        String detail = "Cước " + tariff.getBaseFee() + "+" + tariff.getFeePerKg() + "/kg -> "
            + request.getBaseFee() + "+" + request.getFeePerKg() + "/kg; " + request.getReason();
        tariff.setBaseFee(request.getBaseFee()); tariff.setFeePerKg(request.getFeePerKg());
        tariff.setUpdatedAt(LocalDateTime.now(clock));
        tariffs.saveAndFlush(tariff);
        audit.record(actor, "TARIFF_UPDATE", serviceType.name(), detail);
    }
}

