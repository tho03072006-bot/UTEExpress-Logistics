package vn.edu.hcmute.uteexpress.service.routing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.StaffBranchRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.admin.AdminUserRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.admin.*;

@Service @Transactional(readOnly = true)
public class StaffBranchService {
    private final StaffBranchRepository staff;
    private final ShipperZoneRepository zones;
    private final BranchRepository branches;
    private final ProvinceAreaRepository areas;
    private final AdminUserRepository users;
    private final StaffAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;
    private final OrderRouteRepository routes;
    public StaffBranchService(StaffBranchRepository staff, ShipperZoneRepository zones, BranchRepository branches,
            ProvinceAreaRepository areas, AdminUserRepository users, StaffAccessPolicy access, AuditService audit, Clock clock,
            OrderRouteRepository routes) {
        this.staff = staff; this.zones = zones; this.branches = branches; this.areas = areas;
        this.users = users; this.access = access; this.audit = audit; this.clock = clock; this.routes = routes;
    }
    public StaffBranchRequest selection(String actor, Long userId) {
        access.requireAdmin(actor);
        var selection = new StaffBranchRequest();
        staff.findByUser_Id(userId).ifPresent(s -> selection.setBranchId(s.getBranch().getId()));
        var assigned = zones.findAllByShipper_Id(userId);
        assigned.stream().filter(ShipperZone::isPrimary).findFirst().ifPresent(z -> selection.setPrimaryAreaCode(z.getArea().getAreaCode()));
        selection.setSecondaryAreaCodes(assigned.stream().filter(z -> !z.isPrimary()).map(z -> z.getArea().getAreaCode()).sorted().toList());
        return selection;
    }
    public StaffBranchRequest selectionByUsername(String actor, String username) {
        access.requireAdmin(actor);
        return selection(actor, users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự.")).getId());
    }
    public boolean hasChanges(String actor, Long userId, StaffBranchRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu thông tin chi nhánh.");
        var old = selection(actor, userId);
        return !Objects.equals(old.getBranchId(), request.getBranchId())
                || !Objects.equals(blank(old.getPrimaryAreaCode()), blank(request.getPrimaryAreaCode()))
                || !new HashSet<>(old.getSecondaryAreaCodes()).equals(new HashSet<>(request.getSecondaryAreaCodes()));
    }
    public boolean hasReferences(Long userId) {
        // Cấu hình của chính nhân sự được FK xóa kèm; các vai trò lịch sử vẫn chặn xóa.
        return staff.existsByAssignedBy_Id(userId) || users.countRouteResponsibilities(userId) > 0
                || users.countLinehaulActions(userId) > 0;
    }

    /** Cùng giao dịch với cấp/sửa tài khoản; khóa người dùng để tuần tự hóa thay đổi chi nhánh và phân công. */
    @Transactional
    public void assign(String actor, AppUser target, StaffBranchRequest request) {
        access.requireAdmin(actor); access.validate(request);
        var user = users.lockUser(target.getId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự."));
        var old = staff.findByUser_Id(user.getId());
        if (user.getRole() != AppUser.Role.SHIPPER && user.getRole() != AppUser.Role.MANAGER) {
            zones.deleteAll(zones.findAllByShipper_Id(user.getId()));
            old.ifPresent(s -> {
                staff.delete(s);
                audit.record(actor, "STAFF_BRANCH_REMOVE", "user:" + user.getId(), "Gỡ phân công chi nhánh khi đổi vai trò.");
            });
            return;
        }
        if (request.getBranchId() == null) throw new IllegalArgumentException("Shipper và Manager phải có chi nhánh.");
        var branch = branches.findById(request.getBranchId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh."));
        boolean changed = old.isEmpty() || !Objects.equals(old.get().getBranch().getId(), branch.getId());
        if (!branch.isActive() && changed) throw new IllegalArgumentException("Chi nhánh đã ngừng nhận nhân sự.");
        var running = List.of(Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.PICKED_UP, Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.FAILED);
        if (changed && user.getRole() == AppUser.Role.SHIPPER) {
            // Đổi chi nhánh (B2): chỉ khi shipper không còn giữ đơn đang chạy.
            if (old.isPresent() && users.countUnfinishedAssignments(user.getId(), running) > 0)
                throw new IllegalArgumentException("Shipper còn đơn đang chạy hoặc chờ xử lý; chưa được đổi chi nhánh.");
            // Gán lần đầu cho shipper có từ trước khi có chi nhánh (B10): được phép nếu không giữ đơn có tuyến của chi nhánh khác.
            // Đơn cũ chưa có tuyến vẫn theo quy tắc chuyển tiếp (hiện cho mọi chi nhánh) nên không chặn.
            if (old.isEmpty() && routes.countHeldOutsideBranch(user.getId(), running, branch.getId()) > 0)
                throw new IllegalArgumentException("Shipper đang giữ đơn thuộc chi nhánh khác; hãy phân công lại các đơn đó trước khi gán chi nhánh " + branch.getCode() + ".");
        }
        var selected = new LinkedHashMap<String, ProvinceArea>();
        if (user.getRole() == AppUser.Role.SHIPPER) {
            if (blank(request.getPrimaryAreaCode()) == null) throw new IllegalArgumentException("Shipper phải có một khu vực chính.");
            var codes = new ArrayList<String>(); codes.add(request.getPrimaryAreaCode()); codes.addAll(request.getSecondaryAreaCodes());
            for (String code : codes) {
                var area = areas.findByAreaCode(code).orElseThrow(() -> new IllegalArgumentException("Khu vực giao nhận không hợp lệ."));
                if (!Objects.equals(area.getBranch().getId(), branch.getId())) throw new IllegalArgumentException("Các khu vực phụ trách phải cùng chi nhánh với Shipper.");
                if (selected.put(code, area) != null) throw new IllegalArgumentException("Không được chọn trùng khu vực chính và phụ.");
            }
        }
        if (!hasChanges(actor, user.getId(), request)) return;
        var admin = users.findByUsername(actor).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người cấp quyền."));
        var now = LocalDateTime.now(clock);
        var assignment = old.orElseGet(() -> new StaffBranch(user, branch, admin, now));
        assignment.assign(branch, admin, now); staff.save(assignment);
        zones.deleteAll(zones.findAllByShipper_Id(user.getId())); zones.flush();
        selected.forEach((code, area) -> zones.save(new ShipperZone(user, area, code.equals(request.getPrimaryAreaCode()))));
        // Service cấp/sửa tài khoản thu hồi phiên đúng một lần trong cùng giao dịch.
        audit.record(actor, "STAFF_BRANCH_ASSIGN", "user:" + user.getId(), "Chi nhánh " + branch.getCode()
                + "; khu vực chính " + Objects.toString(request.getPrimaryAreaCode(), "không có") + "; số khu vực " + selected.size());
    }
    private static String blank(String text) { return text == null || text.isBlank() ? null : text; }
}
