package vn.edu.hcmute.uteexpress.service.admin.impl;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.repository.EmailChangeRequestRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;

/** CRUD của cả bốn vai trò; luôn kiểm tra lại Admin thật từ DB. */
@Service @Transactional
public class AdminAccountServiceImpl implements AdminAccountService {
    private final AdminUserRepository users;
    private final StaffAccountRepository staff;
    private final EmailChangeRequestRepository emailChanges;
    private final StaffAccessPolicy access;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final vn.edu.hcmute.uteexpress.security.admin.StaffLoginAttemptService attempts;
    public AdminAccountServiceImpl(AdminUserRepository users, StaffAccountRepository staff,
            EmailChangeRequestRepository emailChanges, StaffAccessPolicy access,
            PasswordEncoder encoder, AuditService audit,
            vn.edu.hcmute.uteexpress.security.admin.StaffLoginAttemptService attempts) {
        this.users = users; this.staff = staff; this.emailChanges = emailChanges;
        this.access = access; this.encoder = encoder; this.audit = audit; this.attempts = attempts;
    }
    @Override @Transactional(readOnly = true)
    public Page<AdminAccountResponse> findAccounts(String actor, AppUser.Role role, int page) {
        access.requireAdmin(actor); access.validatePage(page);
        return users.findAccounts(role, PageRequest.of(page, 20)).map(AdminAccountResponse::from);
    }
    @Override @Transactional(readOnly = true)
    public AdminAccountResponse findAccount(String actor, Long id) {
        access.requireAdmin(actor); access.validateId(id);
        return AdminAccountResponse.from(users.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản. Hãy tải lại danh sách.")));
    }
    @Override public void createAccount(String actor, AdminAccountCreateRequest form) {
        lockAdmin(actor); access.validate(form);
        if (users.existsByUsernameIgnoreCase(form.getUsername())) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại, kể cả tài khoản chưa xác thực.");
        }
        if (users.existsByEmailIgnoreCase(form.getEmail())) {
            throw new IllegalArgumentException("Email đã được tài khoản khác sử dụng.");
        }
        var user = new AppUser(form.getUsername(), encoder.encode(form.getCredentials().getPassword()), form.getEmail());
        applyProfile(user, form); user.setEnabled(true);
        users.save(user);
        if (isInternal(user.getRole())) {
            var account = new StaffAccount(); account.setUser(user); account.setIssuedBy(actor);
            // Mật khẩu bàn giao của Admin/Manager luôn phải đổi ở lần đăng nhập đầu.
            account.setPasswordChangeRequired(true); staff.save(account);
        }
        staff.flush();
        audit.record(actor, "ACCOUNT_CREATE", "user:" + user.getId(), "Cấp mới role=" + user.getRole() + "; không dùng OTP.");
    }
    @Override public void updateAccount(String actor, Long id, AdminAccountUpdateRequest form) {
        var locked = lockAdmin(actor); access.validate(form);
        confirmAdminPassword(locked, actor, form.getCurrentPassword());
        var user = target(id, form.getRevision()); rejectSelf(actor, user);
        var profile = form.getProfile(); var oldRole = user.getRole();
        if (isInternal(oldRole) != isInternal(profile.getRole())) {
            throw new IllegalArgumentException("Không chuyển tài khoản OTP thành Admin/Manager hoặc ngược lại. Hãy cấp tài khoản riêng đúng vai trò.");
        }
        if (oldRole != profile.getRole() && !isInternal(oldRole) && hasLinkedData(user)) {
            throw new IllegalArgumentException("Tài khoản có dữ liệu nghiệp vụ, không được đổi User/Shipper. Hãy cấp tài khoản riêng để giữ lịch sử.");
        }
        if (user.getRole() == AppUser.Role.SHIPPER && !form.getActive()
                && users.countUnfinishedAssignments(id, unfinishedStatuses()) > 0) {
            throw new IllegalArgumentException("Shipper còn đơn chưa hoàn tất. Cần xử lý/điều phối các đơn đó trước khi khóa.");
        }
        var account = internalAccount(locked, user);
        protectLastAdmin(locked, user, form.getActive() && profile.getRole() == AppUser.Role.ADMIN);
        if (Objects.equals(user.getFullName(), profile.getFullName()) && Objects.equals(user.getEmail(), profile.getEmail())
                && Objects.equals(user.getPhone(), profile.getPhone()) && oldRole == profile.getRole()
                && user.isEnabled() == form.getActive() && user.isLockedByAdmin() == !form.getActive()
                && (account == null || account.isActive() == form.getActive())) {
            throw new IllegalArgumentException("Chưa có thay đổi thông tin, quyền hoặc trạng thái.");
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(profile.getEmail(), id)) {
            throw new IllegalArgumentException("Email đã được tài khoản khác sử dụng.");
        }
        applyProfile(user, profile); user.setEnabled(form.getActive()); user.setLockedByAdmin(!form.getActive());
        clearRecovery(user); user.revokeSessions();
        if (account != null) { account.setActive(form.getActive()); account.revokeSessions(); }
        staff.flush();
        audit.record(actor, "ACCOUNT_UPDATE", "user:" + id,
            "role=" + oldRole + "->" + user.getRole() + "; active=" + form.getActive() + "; " + form.getReason());
    }
    @Override public void deleteAccount(String actor, Long id, AdminAccountDeleteRequest form) {
        var locked = lockAdmin(actor); access.validate(form);
        confirmAdminPassword(locked, actor, form.getCurrentPassword());
        var user = target(id, form.getRevision()); rejectSelf(actor, user);
        if (!user.getUsername().equals(form.getConfirmedUsername())) {
            throw new IllegalArgumentException("Tên đăng nhập xác nhận không khớp với tài khoản cần xóa.");
        }
        protectLastAdmin(locked, user, false);
        if (hasLinkedData(user)) {
            throw new IllegalArgumentException("Không thể xóa tài khoản có đơn hàng, địa chỉ, giỏ đơn, dấu vết biểu mẫu, đánh giá, bằng chứng, yêu cầu đổi email hoặc lịch sử cấp tài khoản. Hãy khóa tài khoản để giữ dữ liệu.");
        }
        var account = internalAccount(locked, user);
        // Không cascade: FK vẫn bảo vệ nếu một nghiệp vụ khác vừa tạo liên kết mới.
        if (account != null) { staff.delete(account); staff.flush(); }
        audit.record(actor, "ACCOUNT_DELETE", "user:" + id,
            "Xóa tài khoản chưa có dữ liệu liên kết; role=" + user.getRole() + "; " + form.getReason());
        users.delete(user); staff.flush();
    }
    @Override public void resetPassword(String actor, Long id, AdminAccountPasswordRequest form) {
        var locked = lockAdmin(actor); access.validate(form);
        confirmAdminPassword(locked, actor, form.getCurrentPassword());
        var user = target(id, form.getRevision()); rejectSelf(actor, user);
        var account = internalAccount(locked, user);
        if (!user.isEnabled() || user.isLockedByAdmin() || (account != null && !account.isActive())) {
            throw new IllegalArgumentException("Tài khoản bị khóa/chưa kích hoạt. Cần kiểm tra và mở trước khi cấp lại mật khẩu.");
        }
        if (encoder.matches(form.getCredentials().getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPassword(encoder.encode(form.getCredentials().getPassword())); clearRecovery(user); user.revokeSessions();
        if (account != null) { account.setPasswordChangeRequired(true); account.revokeSessions(); }
        staff.flush();
        audit.record(actor, "ACCOUNT_PASSWORD_RESET", "user:" + id, "Cấp lại mật khẩu; thu hồi phiên cũ; " + form.getReason());
    }
    private List<StaffAccount> lockAdmin(String actor) {
        // Cùng thứ tự khóa với nghiệp vụ cấp/đổi quyền nội bộ cũ.
        var locked = staff.lockAccounts();
        var account = locked.stream().filter(s -> s.getUser().getUsername().equals(actor)).findFirst()
            .orElseThrow(() -> new AccessDeniedException("Cần tài khoản Admin nội bộ được cấp."));
        if (!account.isActive() || !account.getUser().isEnabled() || account.getUser().isLockedByAdmin()
                || account.isPasswordChangeRequired() || account.getUser().getRole() != AppUser.Role.ADMIN) {
            throw new AccessDeniedException("Chỉ Admin đang hoạt động mới được quản lý tài khoản.");
        }
        return locked;
    }
    private void confirmAdminPassword(List<StaffAccount> locked, String actor, String password) {
        var admin = locked.stream().filter(s -> s.getUser().getUsername().equals(actor)).findFirst().orElseThrow();
        String confirmationKey = "crud:" + admin.getUser().getId();
        if (attempts.isBlocked(confirmationKey)) {
            throw new IllegalArgumentException("Đã xác nhận sai quá nhiều lần. Vui lòng thử lại sau 15 phút.");
        }
        if (!encoder.matches(password, admin.getUser().getPassword())) {
            attempts.recordFailure(confirmationKey);
            throw new IllegalArgumentException("Mật khẩu xác nhận của Admin không đúng.");
        }
        attempts.clearFailures(confirmationKey);
    }
    private AppUser target(Long id, long revision) {
        access.validateId(id);
        var user = users.lockUser(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản. Hãy tải lại danh sách."));
        if (user.getRevision() != revision) { throw new StaffConflictException("Tài khoản đã thay đổi. Hãy tải lại trang trước khi thao tác."); }
        return user;
    }
    private void rejectSelf(String actor, AppUser user) {
        if (user.getUsername().equals(actor)) { throw new IllegalArgumentException("Không sửa/xóa/cấp lại mật khẩu của chính mình tại trang quản trị. Dùng Đổi mật khẩu nội bộ."); }
    }
    private void protectLastAdmin(List<StaffAccount> locked, AppUser user, boolean remainsAdmin) {
        if (user.getRole() == AppUser.Role.ADMIN && !remainsAdmin
                && locked.stream().noneMatch(s -> !Objects.equals(s.getUser().getId(), user.getId())
                    && s.isActive() && s.getUser().isEnabled() && !s.getUser().isLockedByAdmin()
                    && s.getUser().getRole() == AppUser.Role.ADMIN)) {
            throw new IllegalArgumentException("Phải giữ ít nhất một Admin hoạt động.");
        }
    }
    private StaffAccount internalAccount(List<StaffAccount> locked, AppUser user) {
        if (!isInternal(user.getRole())) { return null; }
        return locked.stream().filter(s -> Objects.equals(s.getUser().getId(), user.getId())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tài khoản nội bộ chưa được cấp hợp lệ. Không chỉnh trực tiếp role trong SQL; cần cấp tài khoản mới."));
    }
    private boolean hasLinkedData(AppUser user) {
        var id = user.getId();
        return users.countOrders(id) > 0 || users.countAddresses(id) > 0 || users.countDrafts(id) > 0 || users.countFormSubmissions(id) > 0
            || users.countReviews(id) > 0 || users.countProofs(id) > 0 || users.countEmailChanges(id) > 0
            || users.countIssuedAccounts(user.getUsername()) > 0 || users.countJourneyActions(id) > 0
            || users.countHandledComplaints(id) > 0 || users.countCashRecords(id) > 0;
    }
    private void clearRecovery(AppUser user) {
        user.setOtpCode(null); user.setOtpExpiry(null);
        emailChanges.deleteByUser(user);
    }
    private void applyProfile(AppUser user, AdminAccountProfileRequest profile) {
        user.setFullName(profile.getFullName()); user.setEmail(profile.getEmail());
        user.setPhone(profile.getPhone()); user.setRole(profile.getRole());
    }
    private boolean isInternal(AppUser.Role role) { return role == AppUser.Role.ADMIN || role == AppUser.Role.MANAGER; }
    private List<Order.OrderStatus> unfinishedStatuses() {
        return List.of(Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.PICKED_UP, Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.FAILED);
    }
}
