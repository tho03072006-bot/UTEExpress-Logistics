package vn.edu.hcmute.uteexpress.controller.profile;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;

/**
 * Họ tên hiện trên thanh trên và menu trái của mọi vai trò (thay cho tên đăng nhập).
 * Chỉ đọc database lần đầu trong phiên rồi giữ trong session; trang "Tài khoản của tôi" làm mới lại khi mở.
 */
@ControllerAdvice(annotations = Controller.class)
public class DisplayNameAdvice {
    public static final String SESSION_KEY = "displayName";

    private final AppUserRepository users;

    public DisplayNameAdvice(AppUserRepository users) { this.users = users; }

    @ModelAttribute("displayName")
    public String displayName(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) return null;
        var session = request.getSession(false);
        if (session == null) return null;
        if (session.getAttribute(SESSION_KEY) instanceof String cached) return cached;
        String name = users.findByUsername(authentication.getName()).map(AppUser::getFullName)
                .filter(full -> full != null && !full.isBlank()).map(String::strip).orElse(authentication.getName());
        session.setAttribute(SESSION_KEY, name);
        return name;
    }
}
