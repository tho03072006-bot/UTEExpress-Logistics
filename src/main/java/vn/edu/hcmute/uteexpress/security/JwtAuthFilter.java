package vn.edu.hcmute.uteexpress.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;

/** Chỉ gắn vào chain /api/internal/**; không đăng ký servlet filter toàn cục. */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokens;
    private final StaffAccountRepository accounts;
    public JwtAuthFilter(JwtTokenProvider tokens, StaffAccountRepository accounts) {
        this.tokens = tokens; this.accounts = accounts;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!tokens.isEnabled()) { respond(response, 503, "API JWT chưa được bật."); return; }
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) { chain.doFilter(request, response); return; }
        if (headers.size() != 1 || !headers.get(0).matches("(?i)Bearer [A-Za-z0-9_.-]+")
                || headers.get(0).length() > 4103) {
            unauthorized(response); return;
        }
        try {
            var claims = tokens.parseToken(headers.get(0).substring(7));
            var account = accounts.findByUserUsername(claims.getSubject()).orElse(null);
            if (!tokens.matchesAccount(claims, account)) { unauthorized(response); return; }
            var principal = new StaffPrincipal(account);
            principal.eraseCredentials();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException ex) {
            unauthorized(response); return;
        } catch (DataAccessException ex) {
            respond(response, 503, "Tạm thời không kiểm tra được quyền truy cập."); return;
        }
        chain.doFilter(request, response);
    }
    private static void unauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setHeader("WWW-Authenticate", "Bearer");
        respond(response, 401, "Token thiếu, hết hạn, bị thu hồi hoặc không hợp lệ.");
    }
    private static void respond(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
