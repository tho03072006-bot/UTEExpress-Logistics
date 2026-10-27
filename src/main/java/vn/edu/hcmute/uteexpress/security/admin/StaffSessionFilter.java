package vn.edu.hcmute.uteexpress.security.admin;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;

public class StaffSessionFilter extends OncePerRequestFilter {
    public static final String AUTHENTICATED_AT = StaffSessionFilter.class.getName() + ".authenticatedAt";
    public static final String LAST_ACTIVITY_AT = StaffSessionFilter.class.getName() + ".lastActivityAt";
    private final StaffAccountRepository accounts;
    private final Clock clock;

    public StaffSessionFilter(StaffAccountRepository accounts) { this(accounts, Clock.systemUTC()); }
    public StaffSessionFilter(StaffAccountRepository accounts, Clock clock) { this.accounts = accounts; this.clock = clock; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal principal) {
            HttpSession currentSession = request.getSession(false);
            if (currentSession != null && hasTimedOut(currentSession, request)) {
                expire(request, response); return;
            }
            java.util.Optional<vn.edu.hcmute.uteexpress.entity.admin.StaffAccount> account;
            try {
                account = accounts.findByUserUsername(principal.getUsername());
            } catch (DataAccessException ex) {
                response.setStatus(503);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Tạm thời không kiểm tra được quyền truy cập. Vui lòng thử lại sau.");
                return;
            }
            if (account.isEmpty() || !principal.matchesAccount(account.get())) {
                expire(request, response); return;
            }
            // Trang xem (GET) dùng lại tài khoản vừa kiểm tra cho các bước kiểm quyền sau, không đọc lại DB (~100 ms mỗi câu).
            if ("GET".equalsIgnoreCase(request.getMethod()))
                request.setAttribute(vn.edu.hcmute.uteexpress.service.admin.StaffAccessPolicy.requestKey(principal.getUsername()), account);
            if (account.get().isPasswordChangeRequired()
                    && !path.equals("/noi-bo/doi-mat-khau") && !path.equals("/noi-bo/dang-xuat")) {
                response.sendRedirect(request.getContextPath() + "/noi-bo/doi-mat-khau");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean hasTimedOut(HttpSession session, HttpServletRequest request) {
        Instant now = clock.instant();
        Instant authenticated = session.getAttribute(AUTHENTICATED_AT) instanceof Instant value ? value : now;
        Instant lastActivity = session.getAttribute(LAST_ACTIVITY_AT) instanceof Instant value ? value : authenticated;
        long idleSeconds = session.getMaxInactiveInterval() > 0 ? session.getMaxInactiveInterval() : 3600;
        if (!now.isBefore(authenticated.plus(Duration.ofHours(8))) || !now.isBefore(lastActivity.plusSeconds(idleSeconds))) return true;
        session.setAttribute(AUTHENTICATED_AT, authenticated);
        // Polling/SockJS/tải CSS không được gia hạn thời gian thao tác của nhân viên.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean staffAction = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())
            && (path.startsWith("/admin/") || path.startsWith("/manager/") || path.startsWith("/noi-bo/"));
        if (staffAction || !vn.edu.hcmute.uteexpress.security.PageRequestCache.isBackgroundRequest(request))
            session.setAttribute(LAST_ACTIVITY_AT, now);
        else if (session.getAttribute(LAST_ACTIVITY_AT) == null) session.setAttribute(LAST_ACTIVITY_AT, lastActivity);
        return false;
    }

    private void expire(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false); if (session != null) session.invalidate();
        if (vn.edu.hcmute.uteexpress.security.PageRequestCache.isBackgroundRequest(request)) {
            response.setStatus(401); response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Phiên nội bộ đã hết hạn hoặc bị thu hồi. Hãy đăng nhập lại.");
        } else response.sendRedirect(request.getContextPath() + "/noi-bo/dang-nhap?expired");
    }
}
