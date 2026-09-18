package vn.edu.hcmute.uteexpress.service.admin.impl;

import jakarta.validation.Validator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccountService;

@Service
@Transactional
public class StaffAccountServiceImpl implements StaffAccountService {
    private static final Logger log = LoggerFactory.getLogger(StaffAccountServiceImpl.class);
    private static final int PAGE_SIZE = 20;
    private final StaffAccountRepository accounts;
    private final AdminUserRepository users;
    private final PasswordEncoder encoder;
    private final Validator validator;

    public StaffAccountServiceImpl(StaffAccountRepository accounts, AdminUserRepository users,
            PasswordEncoder encoder, Validator validator) {
        this.accounts = accounts;
        this.users = users;
        this.encoder = encoder;
        this.validator = validator;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StaffAccountResponse> findAccounts(String actor, int page) {
        requireAdmin(accounts.findByUserUsername(actor).orElseThrow(this::denied));
        if (page < 0 || page > 10000) {
            throw new IllegalArgumentException("Số trang phải từ 0 đến 10000.");
        }
        return accounts.findAll(PageRequest.of(page, PAGE_SIZE, Sort.by("id").descending()))
                .map(StaffAccountResponse::from);
    }

    @Override
    public void createAccount(String actor, StaffAccountCreateRequest request) {
        requireAdmin(findActor(accounts.lockAccounts(), actor));
        validate(request);
        if (users.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại, kể cả tài khoản chưa xác thực.");
        }
        if (users.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Email đã được sử dụng. Không chuyển tài khoản đăng ký công khai thành tài khoản nội bộ.");
        }
        AppUser user = new AppUser(request.getUsername(), encoder.encode(request.getPassword()), request.getEmail());
        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setEnabled(true);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        StaffAccount account = new StaffAccount();
        account.setUser(users.save(user));
        account.setIssuedBy(actor);
        accounts.saveAndFlush(account);
        log.info("STAFF_CREATE actor={} staffId={} role={}", actor, account.getId(), user.getRole());
    }

    @Override
    public void updateAccount(String actor, Long id, StaffAccountUpdateRequest request) {
        validate(request);
        List<StaffAccount> locked = accounts.lockAccounts();
        requireAdmin(findActor(locked, actor));
        StaffAccount target = findTarget(locked, id, request.getRevision());
        if (target.getUser().getUsername().equals(actor)) {
            throw new IllegalArgumentException("Không được tự khóa hoặc thay đổi cấp quyền của chính mình.");
        }
        boolean removesAdmin = target.isActive() && target.getUser().isEnabled()
                && target.getUser().getRole() == AppUser.Role.ADMIN
                && (!request.getActive() || request.getRole() != AppUser.Role.ADMIN);
        if (removesAdmin && locked.stream().filter(this::isActiveAdmin).count() <= 1) {
            throw new IllegalArgumentException("Hệ thống phải còn ít nhất một Admin đang hoạt động.");
        }
        if (target.isActive() == request.getActive()
                && target.getUser().isEnabled() == request.getActive()
                && target.getUser().getRole() == request.getRole()) {
            throw new IllegalArgumentException("Chưa có thay đổi trạng thái hoặc cấp quyền.");
        }
        target.setActive(request.getActive());
        target.getUser().setEnabled(request.getActive());
        target.getUser().setRole(request.getRole());
        clearOtp(target.getUser());
        target.revokeSessions();
        accounts.flush();
        log.info("STAFF_UPDATE actor={} staffId={} role={} active={}", actor, id,
                request.getRole(), request.getActive());
    }

    @Override
    public void resetPassword(String actor, Long id, long revision, StaffPasswordRequest request) {
        validate(request);
        List<StaffAccount> locked = accounts.lockAccounts();
        requireAdmin(findActor(locked, actor));
        StaffAccount target = findTarget(locked, id, revision);
        if (target.getUser().getUsername().equals(actor)) {
            throw new IllegalArgumentException("Dùng chức năng Đổi mật khẩu để thay mật khẩu của chính mình.");
        }
        if (!target.isActive() || !target.getUser().isEnabled()) {
            throw new IllegalArgumentException("Tài khoản đang bị khóa. Cần mở khóa trước khi cấp lại mật khẩu.");
        }
        setPassword(target, request.getPassword());
        target.setPasswordChangeRequired(true);
        accounts.flush();
        log.info("STAFF_PASSWORD_RESET actor={} staffId={}", actor, id);
    }

    @Override
    public void changePassword(String actor, StaffPasswordChangeRequest request) {
        validate(request);
        StaffAccount account = findActor(accounts.lockAccounts(), actor);
        requireActive(account);
        if (!encoder.matches(request.getCurrentPassword(), account.getUser().getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }
        setPassword(account, request.getPassword());
        account.setPasswordChangeRequired(false);
        accounts.flush();
        log.info("STAFF_PASSWORD_CHANGE staffId={}", account.getId());
    }

    private void setPassword(StaffAccount account, String password) {
        if (encoder.matches(password, account.getUser().getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        account.getUser().setPassword(encoder.encode(password));
        clearOtp(account.getUser());
        account.revokeSessions();
    }

    private void clearOtp(AppUser user) {
        user.setOtpCode(null);
        user.setOtpExpiry(null);
    }

    private StaffAccount findTarget(List<StaffAccount> locked, Long id, long revision) {
        if (id == null || id <= 0 || revision < 0) {
            throw new IllegalArgumentException("Mã hoặc phiên bản tài khoản không hợp lệ.");
        }
        StaffAccount account = locked.stream().filter(s -> s.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản nội bộ."));
        if (account.getRevision() != revision) {
            throw new IllegalArgumentException("Tài khoản vừa được người khác cập nhật. Hãy tải lại danh sách.");
        }
        return account;
    }

    private StaffAccount findActor(List<StaffAccount> locked, String actor) {
        return locked.stream().filter(s -> s.getUser().getUsername().equals(actor))
                .findFirst().orElseThrow(this::denied);
    }

    private void requireAdmin(StaffAccount account) {
        requireActive(account);
        if (account.getUser().getRole() != AppUser.Role.ADMIN || account.isPasswordChangeRequired()) {
            throw denied();
        }
    }

    private void requireActive(StaffAccount account) {
        if (!account.isActive() || !account.getUser().isEnabled()
                || (account.getUser().getRole() != AppUser.Role.ADMIN
                && account.getUser().getRole() != AppUser.Role.MANAGER)) {
            throw denied();
        }
    }

    private boolean isActiveAdmin(StaffAccount account) {
        return account.isActive() && account.getUser().isEnabled()
                && account.getUser().getRole() == AppUser.Role.ADMIN;
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Bạn không có quyền thực hiện thao tác này.");
    }

    private void validate(Object request) {
        if (request == null) { throw new IllegalArgumentException("Thiếu dữ liệu yêu cầu."); }
        var errors = validator.validate(request);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.iterator().next().getMessage());
        }
    }
}
