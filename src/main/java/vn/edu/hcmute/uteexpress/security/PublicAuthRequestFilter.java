package vn.edu.hcmute.uteexpress.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public final class PublicAuthRequestFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS=Set.of("/dang-nhap","/dang-ky","/quen-mat-khau","/dat-lai-mat-khau","/xac-thuc-otp","/xac-thuc-otp/gui-lai");
    private final PublicAuthRateGuard guard;
    private final vn.edu.hcmute.uteexpress.repository.AppUserRepository users;
    private static final String IDENTITY_ATTRIBUTE = PublicAuthRequestFilter.class.getName()+".identity";
    public PublicAuthRequestFilter(PublicAuthRateGuard guard, vn.edu.hcmute.uteexpress.repository.AppUserRepository users) {
        this.guard=guard; this.users=users;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(!request.getMethod().equals("POST") || !PATHS.contains(path)) { chain.doFilter(request,response); return; }
        long retry=guard.checkRequest(path,request.getRemoteAddr());
        if(retry>0) {
            response.setStatus(429); response.setHeader("Retry-After",Long.toString(retry)); response.setHeader("Cache-Control","no-store");
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!doctype html><html lang=\"vi\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Vui lòng thử lại sau - UTEExpress</title><main><h1>Thao tác quá nhanh</h1><p>Vui lòng thử lại sau ít phút. Yêu cầu OTP còn có giới hạn riêng theo tài khoản.</p></main></html>"); return;
        }
        if(path.equals("/dang-nhap")) {
            String username=request.getParameter("username"),password=request.getParameter("password");
            String typed=username==null?"":username.strip();
            String identity="unknown:"+typed;
            if (!typed.isBlank() && typed.length()<=100) {
                try {
                    var account=typed.contains("@") ? users.findByEmailIgnoreCase(typed) : users.findByUsername(typed);
                    if (account.isPresent()) identity=accountIdentity(account.get().getId());
                } catch (org.springframework.dao.DataAccessException ex) {
                    // Database lỗi: không bỏ qua khóa, không ghi nhận thành một lần sai mật khẩu.
                    response.setStatus(503); response.setHeader("Retry-After","5");
                    response.setHeader("Cache-Control","no-store"); response.setContentType("text/plain;charset=UTF-8");
                    response.getWriter().write("Tạm thời chưa thể đăng nhập. Vui lòng thử lại sau."); return;
                }
            }
            request.setAttribute(IDENTITY_ATTRIBUTE, identity);
            if (guard.loginBlocked(identity)) {
                rememberLoginFailure(request,guard,username);
                response.setHeader("Cache-Control","no-store");
                response.sendRedirect(request.getContextPath()+"/dang-nhap?error&locked"); return;
            }
            if(username==null || username.length()>100 || password==null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) {
                guard.loginFailed(identity); rememberLoginFailure(request,guard,username);
                response.sendRedirect(request.getContextPath()+"/dang-nhap?error"); return;
            }
        }
        chain.doFilter(request,response);
    }

    /** Thuộc tính phiên để templates/auth/login.html hiện "sai lần N/10" và giữ lại tên đã gõ (không giữ mật khẩu). */
    public static final String FAILURES_ATTRIBUTE="loginFailures";
    public static final String LIMIT_ATTRIBUTE="loginFailureLimit";
    public static final String USERNAME_ATTRIBUTE="loginLastUsername";

    public static String accountIdentity(Long id) { return "account:"+id; }
    public static String loginIdentity(HttpServletRequest request) {
        Object identity=request.getAttribute(IDENTITY_ATTRIBUTE);
        if (!(identity instanceof String value)) throw new IllegalStateException("Thiếu định danh kiểm soát đăng nhập.");
        return value;
    }

    public static void rememberLoginFailure(HttpServletRequest request,PublicAuthRateGuard guard,String username) {
        var session=request.getSession(false);
        if(session==null) return;
        String typed=username==null?"":username.strip();
        session.setAttribute(FAILURES_ATTRIBUTE,guard.loginFailures(loginIdentity(request)));
        session.setAttribute(LIMIT_ATTRIBUTE,PublicAuthRateGuard.MAX_LOGIN_FAILURES);
        session.setAttribute(USERNAME_ATTRIBUTE,typed.substring(0,Math.min(100,typed.length())));
    }

    public static void forgetLoginFailures(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null) return;
        session.removeAttribute(FAILURES_ATTRIBUTE); session.removeAttribute(LIMIT_ATTRIBUTE); session.removeAttribute(USERNAME_ATTRIBUTE);
    }
}
