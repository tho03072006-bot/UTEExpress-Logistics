package vn.edu.hcmute.uteexpress.service.admin;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;

/** Kiểm tra lại quyền từ DB tại Service, không tin role/username do form gửi. */
@Component
public class StaffAccessPolicy {
    private final StaffAccountRepository accounts;
    private final Validator validator;
    public StaffAccessPolicy(StaffAccountRepository accounts, Validator validator) {
        this.accounts = accounts;
        this.validator = validator;
    }
    public void requireOperator(String actor) { requireRole(actor, false); }
    public void requireAdmin(String actor) { requireRole(actor, true); }
    private void requireRole(String actor, boolean adminOnly) {
        if (actor == null || actor.isBlank()) { throw new AccessDeniedException("Cần đăng nhập nội bộ."); }
        var account = accounts.findByUserUsername(actor)
            .orElseThrow(() -> new AccessDeniedException("Tài khoản nội bộ không hợp lệ."));
        var role = account.getUser().getRole();
        if (!account.isActive() || !account.getUser().isEnabled() || account.getUser().isLockedByAdmin() || account.isPasswordChangeRequired()
            || (role != AppUser.Role.ADMIN && role != AppUser.Role.MANAGER)
            || (adminOnly && role != AppUser.Role.ADMIN)) {
            throw new AccessDeniedException("Không có quyền thực hiện chức năng này.");
        }
    }
    public void validate(Object form) {
        if (form == null) { throw new IllegalArgumentException("Thiếu dữ liệu yêu cầu."); }
        var errors = validator.validate(form);
        if (!errors.isEmpty()) { throw new IllegalArgumentException(errors.iterator().next().getMessage()); }
    }
    public void validatePage(int page) {
        if (page < 0 || page > 10000) { throw new IllegalArgumentException("Số trang phải từ 0 đến 10000."); }
    }
    public void validateId(Long id) {
        if (id == null || id <= 0) { throw new IllegalArgumentException("Mã bản ghi phải là số dương."); }
    }
}
