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
        var account = loadForRequest(actor)
            .orElseThrow(() -> new AccessDeniedException("Tài khoản nội bộ không hợp lệ."));
        var role = account.getUser().getRole();
        if (!account.isActive() || !account.getUser().isEnabled() || account.getUser().isLockedByAdmin() || account.isPasswordChangeRequired()
            || (role != AppUser.Role.ADMIN && role != AppUser.Role.MANAGER)
            || (adminOnly && role != AppUser.Role.ADMIN)) {
            throw new AccessDeniedException("Không có quyền thực hiện chức năng này.");
        }
    }
    /**
     * Một trang tổng quan gọi kiểm tra quyền ở nhiều khối số liệu (mỗi khối một giao dịch riêng): đọc tài khoản
     * nội bộ một lần cho cả lượt tải trang (mỗi câu SQL lên Aiven ~100 ms). Lượt tải sau đọc lại từ DB nên khóa/thu hồi
     * quyền vẫn có hiệu lực ngay. Chỉ dùng cho trang xem (GET): thao tác ghi (vd. tự khóa tài khoản) luôn kiểm tra lại
     * từ DB ở mỗi bước. Ngoài request web (job, test gọi thẳng service) thì luôn đọc DB.
     */
    @SuppressWarnings("unchecked")
    private java.util.Optional<vn.edu.hcmute.uteexpress.entity.admin.StaffAccount> loadForRequest(String actor) {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)
                || !"GET".equalsIgnoreCase(servlet.getRequest().getMethod())) return accounts.findByUserUsername(actor);
        String key = requestKey(actor);
        var scope = org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;
        var cached = attributes.getAttribute(key, scope);
        if (cached instanceof java.util.Optional<?> found) return (java.util.Optional<vn.edu.hcmute.uteexpress.entity.admin.StaffAccount>) found;
        var loaded = accounts.findByUserUsername(actor);
        attributes.setAttribute(key, loaded, scope);
        return loaded;
    }

    /** Khóa request chứa tài khoản nội bộ đã đọc trong lượt GET này (StaffSessionFilter đặt sẵn khi kiểm tra phiên). */
    public static String requestKey(String actor) { return StaffAccessPolicy.class.getName() + ":" + actor; }
    /** Tài khoản nội bộ đã đọc trong lượt GET hiện tại, hoặc null nếu chưa có / không phải GET (khi đó phải đọc DB). */
    @SuppressWarnings("unchecked")
    public static java.util.Optional<vn.edu.hcmute.uteexpress.entity.admin.StaffAccount> readInThisGet(String actor) {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)
                || !"GET".equalsIgnoreCase(servlet.getRequest().getMethod())) return null;
        var cached = attributes.getAttribute(requestKey(actor), org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        return cached instanceof java.util.Optional<?> found ? (java.util.Optional<vn.edu.hcmute.uteexpress.entity.admin.StaffAccount>) found : null;
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
