package vn.edu.hcmute.uteexpress.service.routing;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.routing.*;

@Service @Transactional(readOnly = true)
public class RoutingCatalogService {
    private final BranchRepository branches;
    private final ProvinceAreaRepository areas;
    private final vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService policies;
    public RoutingCatalogService(BranchRepository branches, ProvinceAreaRepository areas, vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService policies) {
        this.branches = branches; this.areas = areas; this.policies = policies;
    }
    /** Mức giảm hiện hành khi khách tự mang hàng ra chi nhánh, để hiện trên form. */
    public java.math.BigDecimal dropOffDiscount() { return policies.current().getDropOffDiscount(); }
    public record AreaOption(String code, String name, String provinceCode, Long branchId, String branchCode) { }
    public List<Branch> branches() { return branches.findAll(org.springframework.data.domain.Sort.by("code")); }
    public List<AreaOption> areas() {
        return areas.findAll(org.springframework.data.domain.Sort.by("areaCode")).stream()
                .map(a -> new AreaOption(a.getAreaCode(), a.getAreaName(), a.getProvinceCode(), a.getBranch().getId(), a.getBranch().getCode())).toList();
    }
    public ProvinceArea requireArea(String provinceCode, String code) {
        if (code == null || code.isBlank() || provinceCode == null || provinceCode.isBlank())
            throw new IllegalArgumentException("Vui lòng chọn tỉnh/thành và khu vực giao nhận.");
        var area = areas.findByAreaCode(code).orElseThrow(() -> new IllegalArgumentException("UTEExpress chưa phục vụ khu vực này"));
        if (!area.getProvinceCode().equals(provinceCode) || !area.getBranch().isActive())
            throw new IllegalArgumentException("UTEExpress chưa phục vụ khu vực này");
        return area;
    }
    public ProvinceArea requireArea(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Vui lòng chọn khu vực giao nhận.");
        var area = areas.findByAreaCode(code).orElseThrow(() -> new IllegalArgumentException("UTEExpress chưa phục vụ khu vực này"));
        if (!area.getBranch().isActive()) throw new IllegalArgumentException("Chi nhánh tạm ngừng nhận đơn mới.");
        return area;
    }
}
