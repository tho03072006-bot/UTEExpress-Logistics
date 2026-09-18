package vn.edu.hcmute.uteexpress.security.admin;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;

public class StaffSessionFilter extends OncePerRequestFilter {
    private final StaffAccountRepository accounts;

    public StaffSessionFilter(StaffAccountRepository accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal principal) {
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
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) { session.invalidate(); }
                response.sendRedirect(request.getContextPath() + "/noi-bo/dang-nhap?expired");
                return;
            }
            if (account.get().isPasswordChangeRequired()
                    && !path.equals("/noi-bo/doi-mat-khau") && !path.equals("/noi-bo/dang-xuat")) {
                response.sendRedirect(request.getContextPath() + "/noi-bo/doi-mat-khau");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
