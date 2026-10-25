package vn.edu.hcmute.uteexpress.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository;

/**
 * Mã chi nhánh (sgn, han, dad...) của Manager đang đăng nhập, để mọi trang của Manager dùng ảnh thành phố của chi nhánh đó.
 * Chỉ tra một lần mỗi phiên đăng nhập (lưu trong session); Admin và các vai trò khác không tra.
 * Chỉ phục vụ giao diện: lỗi khi tra thì bỏ qua và dùng ảnh mặc định.
 */
@ControllerAdvice
public class StaffBranchPhotoAdvice {
    static final String SESSION_KEY = "ute.staffBranchPhoto";
    private final ObjectProvider<StaffBranchRepository> staffBranches;

    public StaffBranchPhotoAdvice(ObjectProvider<StaffBranchRepository> staffBranches) {
        this.staffBranches = staffBranches;
    }

    @ModelAttribute("staffBranchPhoto")
    public String staffBranchPhoto(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || !hasRole(authentication, "ROLE_MANAGER")
                || hasRole(authentication, "ROLE_ADMIN")) return null;
        var session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_KEY) instanceof String cached) return cached.isEmpty() ? null : cached;
        var repository = staffBranches.getIfAvailable();
        if (repository == null) return null;
        String code;
        try {
            code = repository.findBranchCodeByUsername(authentication.getName())
                    .map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> value.matches("[a-z]{2,10}")).orElse("");
        } catch (RuntimeException ex) {
            return null;
        }
        if (session != null) session.setAttribute(SESSION_KEY, code);
        return code.isEmpty() ? null : code;
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
