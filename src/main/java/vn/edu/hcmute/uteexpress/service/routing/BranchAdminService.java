package vn.edu.hcmute.uteexpress.service.routing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.BranchUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.routing.Branch;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.admin.*;

@Service @Transactional(readOnly = true)
public class BranchAdminService {
    private final BranchRepository branches;
    private final ProvinceAreaRepository areas;
    private final StaffAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;
    public BranchAdminService(BranchRepository branches, ProvinceAreaRepository areas,
            StaffAccessPolicy access, AuditService audit, Clock clock) {
        this.branches = branches; this.areas = areas; this.access = access; this.audit = audit; this.clock = clock;
    }
    public List<Branch> list(String actor) {
        access.requireAdmin(actor); return branches.findAll(Sort.by("code"));
    }
    public Branch get(String actor, Long id) {
        access.requireAdmin(actor); access.validateId(id);
        return branches.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh."));
    }
    @Transactional public void update(String actor, Long id, BranchUpdateRequest form) {
        access.requireAdmin(actor); access.validateId(id); access.validate(form);
        var branch = branches.lockById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh."));
        if (branch.getRevision() != form.getRevision())
            throw new StaffConflictException("Chi nhánh vừa được cập nhật. Hãy tải lại trước khi sửa.");
        var area = areas.findByAreaCode(form.getHubAreaCode())
                .orElseThrow(() -> new IllegalArgumentException("Khu vực kho không hợp lệ."));
        if (!Objects.equals(area.getBranch().getId(), id) || !area.getProvinceCode().equals(branch.getProvinceCode()))
            throw new IllegalArgumentException("Kho phải nằm trong khu vực của chính chi nhánh này.");
        if (branch.getName().equals(form.getName()) && branch.getHubAddress().equals(form.getHubAddress())
                && branch.getHubAreaCode().equals(form.getHubAreaCode()) && branch.isActive() == form.getActive())
            throw new IllegalArgumentException("Chưa có thay đổi thông tin chi nhánh.");
        String before = "khu vực=" + branch.getHubAreaCode() + "; nhận đơn=" + branch.isActive();
        boolean nameChanged = !branch.getName().equals(form.getName());
        boolean addressChanged = !branch.getHubAddress().equals(form.getHubAddress());
        branch.configure(form.getName(), form.getHubAddress(), form.getHubAreaCode(), form.getActive(), LocalDateTime.now(clock));
        branches.flush();
        audit.record(actor, "BRANCH_UPDATE", "branch:" + id, before + " -> " + branch.getHubAreaCode()
                + "; nhận đơn=" + branch.isActive() + "; đổi tên=" + nameChanged + "; đổi địa chỉ=" + addressChanged + "; " + form.getReason());
    }
}
