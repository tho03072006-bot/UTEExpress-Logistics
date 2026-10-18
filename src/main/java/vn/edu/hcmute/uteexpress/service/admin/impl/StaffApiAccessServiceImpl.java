package vn.edu.hcmute.uteexpress.service.admin.impl;

import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.JwtTokenProvider;
import vn.edu.hcmute.uteexpress.security.admin.StaffLoginAttemptService;
import vn.edu.hcmute.uteexpress.service.admin.*;

@Service @Transactional
public class StaffApiAccessServiceImpl implements StaffApiAccessService {
    private final StaffAccountRepository accounts;
    private final StaffAccessPolicy access;
    private final JwtTokenProvider tokens;
    private final PasswordEncoder encoder;
    private final StaffLoginAttemptService attempts;
    private final AuditService audit;
    public StaffApiAccessServiceImpl(StaffAccountRepository accounts, StaffAccessPolicy access,
            JwtTokenProvider tokens, PasswordEncoder encoder, StaffLoginAttemptService attempts, AuditService audit) {
        this.accounts = accounts; this.access = access; this.tokens = tokens;
        this.encoder = encoder; this.attempts = attempts; this.audit = audit;
    }
    public String issueToken(String actor, ApiAccessRequest request) {
        access.requireAdmin(actor);
        access.validate(request);
        accounts.lockAccounts(); access.requireAdmin(actor);
        var account = accounts.findByUserUsername(actor).orElseThrow();
        confirmPassword(account, request.password());
        if (!tokens.isEnabled()) throw new IllegalArgumentException("Chưa bật JWT bằng khóa riêng. Xem config/jwt-local.properties.example.");
        String token = tokens.generateToken(account);
        // Không ghi mật khẩu, token hoặc khóa ký vào nhật ký/database.
        audit.record(actor, "API_TOKEN_ISSUE", "staff:" + account.getId(), "API chỉ đọc; thời hạn " + tokens.getLifetimeSeconds() / 60 + " phút.");
        return token;
    }
    @Transactional(readOnly = true)
    public Page<StaffAccessResponse> findAccess(String actor, int page) {
        access.requireAdmin(actor); access.validatePage(page);
        return accounts.findAll(PageRequest.of(page, 20, Sort.by("id"))).map(StaffAccessResponse::from);
    }
    public void revokeAccess(String actor, Long id, AccessRevocationRequest request) {
        access.requireAdmin(actor); access.validateId(id); access.validate(request);
        List<StaffAccount> locked = accounts.lockAccounts(); access.requireAdmin(actor);
        StaffAccount current = locked.stream().filter(account -> account.getUser().getUsername().equals(actor)).findFirst().orElseThrow();
        confirmPassword(current, request.password());
        var target = locked.stream().filter(account -> account.getId().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tài khoản nội bộ không còn tồn tại."));
        if (target.getRevision() != request.revision()) throw new StaffConflictException("Tài khoản đã thay đổi; hãy tải lại trang.");
        if (target.getUser().getUsername().equals(actor) && !Boolean.TRUE.equals(request.confirmSelf()))
            throw new IllegalArgumentException("Cần xác nhận sẽ đăng xuất trên mọi máy khi thu hồi truy cập của chính mình.");
        if (request.reason().strip().length() < 5) throw new IllegalArgumentException("Lý do cần ít nhất 5 ký tự có nội dung.");
        target.revokeSessions(); accounts.flush();
        audit.record(actor, "ACCESS_REVOKE", "staff:" + id, "Thu hồi tất cả phiên web và JWT; " + request.reason().strip());
    }
    private void confirmPassword(StaffAccount actor, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Mật khẩu xác nhận không vượt quá 72 byte UTF-8.");
        String key = "api-confirm:" + actor.getUser().getId();
        if (attempts.isBlocked(key)) throw new IllegalArgumentException("Xác nhận sai quá nhiều lần; thử lại sau 15 phút.");
        if (!encoder.matches(password, actor.getUser().getPassword())) {
            attempts.recordFailure(key); throw new IllegalArgumentException("Mật khẩu xác nhận không đúng.");
        }
        attempts.clearFailures(key);
    }
}
