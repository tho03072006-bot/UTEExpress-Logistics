package vn.edu.hcmute.uteexpress.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chặn dò mật khẩu / dò mã OTP theo địa chỉ IP (bổ sung cho việc khóa tài khoản nội bộ sau 5 lần sai).
 * - Đăng nhập (khách hàng và nội bộ): chỉ đếm lần SAI; quá {@code maxLoginFailures} lần trong cửa sổ thời gian thì tạm chặn.
 * - Quên mật khẩu, nhập OTP, đặt lại mật khẩu: đếm mọi lần gửi.
 * Lưu trong bộ nhớ của một máy chủ; khởi động lại thì xóa. Tắt được bằng app.security.login-limit.enabled=false.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> LOGIN_PATHS = Set.of("/dang-nhap", "/noi-bo/dang-nhap");
    private static final Set<String> OTP_PATHS = Set.of("/quen-mat-khau", "/xac-thuc-otp", "/dat-lai-mat-khau");
    private static final int MAX_TRACKED_KEYS = 20_000;

    private final boolean enabled;
    private final int maxLoginFailures;
    private final int maxOtpRequests;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(@Value("${app.security.login-limit.enabled:true}") boolean enabled,
                                @Value("${app.security.login-limit.max-failures:20}") int maxLoginFailures,
                                @Value("${app.security.login-limit.max-otp-requests:15}") int maxOtpRequests,
                                @Value("${app.security.login-limit.window-minutes:10}") long windowMinutes) {
        this.enabled = enabled;
        this.maxLoginFailures = Math.max(1, maxLoginFailures);
        this.maxOtpRequests = Math.max(1, maxOtpRequests);
        this.windowMillis = Duration.ofMinutes(Math.max(1, windowMinutes)).toMillis();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = path(request);
        return !LOGIN_PATHS.contains(path) && !OTP_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = path(request);
        boolean login = LOGIN_PATHS.contains(path);
        String key = (login ? "login|" : path + "|") + request.getRemoteAddr();
        int limit = login ? maxLoginFailures : maxOtpRequests;
        long now = System.currentTimeMillis();
        if (count(key, now) >= limit) {
            if (login) response.sendRedirect(request.getContextPath() + path + "?too-many");
            else response.sendError(429, "Bạn thử quá nhiều lần. Hãy chờ ít phút rồi thử lại.");
            return;
        }
        if (!login) record(key, now);
        chain.doFilter(request, response);
        if (login) {
            String location = response.getHeader("Location");
            boolean failed = location != null && location.contains("error");
            if (failed) record(key, now);
            // Trang đăng nhập khách hàng/Shipper đọc số này để hiện "sai lần N/20" (trang nội bộ có bộ đếm riêng theo tài khoản).
            var session = request.getSession(false);
            if (session != null && "/dang-nhap".equals(path)) {
                if (failed) {
                    session.setAttribute(FAILURES_ATTRIBUTE, count(key, now));
                    session.setAttribute(LIMIT_ATTRIBUTE, limit);
                    // Giữ tên đã gõ (không giữ mật khẩu) để khỏi nhập lại; Thymeleaf tự escape khi hiển thị.
                    String typed = request.getParameter("username") == null ? "" : request.getParameter("username").strip();
                    session.setAttribute(USERNAME_ATTRIBUTE, typed.substring(0, Math.min(50, typed.length())));
                } else if (location != null) {
                    session.removeAttribute(FAILURES_ATTRIBUTE); session.removeAttribute(LIMIT_ATTRIBUTE); session.removeAttribute(USERNAME_ATTRIBUTE);
                }
            }
        }
    }

    /** Thuộc tính phiên để templates/auth/login.html hiện số lần đăng nhập sai từ máy này. */
    public static final String FAILURES_ATTRIBUTE = "loginFailures";
    public static final String LIMIT_ATTRIBUTE = "loginFailureLimit";
    public static final String USERNAME_ATTRIBUTE = "loginLastUsername";

    private String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private int count(String key, long now) {
        Deque<Long> times = hits.get(key);
        if (times == null) return 0;
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > windowMillis) times.pollFirst();
            return times.size();
        }
    }

    private void record(String key, long now) {
        if (hits.size() > MAX_TRACKED_KEYS) hits.entrySet().removeIf(e -> { synchronized (e.getValue()) {
            return e.getValue().isEmpty() || now - e.getValue().peekLast() > windowMillis; } });
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) { times.addLast(now); }
    }
}
