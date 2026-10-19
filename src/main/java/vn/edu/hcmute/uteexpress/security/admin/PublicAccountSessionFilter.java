package vn.edu.hcmute.uteexpress.security.admin;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;

/** Khóa/xóa/đổi quyền/mật khẩu có hiệu lực cả với phiên User/Shipper đã đăng nhập. */
public class PublicAccountSessionFilter extends OncePerRequestFilter {
    private final AppUserRepository users;
    public PublicAccountSessionFilter(AppUserRepository users) { this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PublicAccountPrincipal principal) {
            boolean valid;
            try { valid = users.findByUsername(principal.getUsername()).map(principal::matchesAccount).orElse(false); }
            catch (DataAccessException ex) {
                response.setStatus(503); response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Tạm thời không kiểm tra được tài khoản. Vui lòng thử lại sau."); return;
            }
            if (!valid) {
                SecurityContextHolder.clearContext(); var session = request.getSession(false);
                if (session != null) { session.invalidate(); }
                String path=request.getRequestURI().substring(request.getContextPath().length());
                if (path.startsWith("/api/assistant/")) {
                    response.setStatus(401); response.setHeader("Cache-Control","no-store"); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"SESSION_EXPIRED\",\"message\":\"Phiên hoặc quyền truy cập đã thay đổi. Vui lòng đăng nhập lại.\"}"); return;
                }
                response.sendRedirect(request.getContextPath() + "/dang-nhap?expired"); return;
            }
        }
        chain.doFilter(request, response);
    }
}
