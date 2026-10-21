package vn.edu.hcmute.uteexpress.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public final class PublicAuthRequestFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS=Set.of("/dang-nhap","/dang-ky","/quen-mat-khau","/dat-lai-mat-khau","/xac-thuc-otp","/xac-thuc-otp/gui-lai");
    private final PublicAuthRateGuard guard;
    public PublicAuthRequestFilter(PublicAuthRateGuard guard) { this.guard=guard; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(!request.getMethod().equals("POST") || !PATHS.contains(path)) { chain.doFilter(request,response); return; }
        long retry=guard.checkRequest(path,request.getRemoteAddr());
        if(path.equals("/dang-nhap") && guard.loginBlocked(request.getParameter("username"))) retry=Math.max(retry,900);
        if(retry>0) {
            response.setStatus(429); response.setHeader("Retry-After",Long.toString(retry)); response.setHeader("Cache-Control","no-store");
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!doctype html><html lang=\"vi\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Vui lòng thử lại sau - UTEExpress</title><main><h1>Thao tác quá nhanh</h1><p>Vui lòng thử lại sau ít phút. Yêu cầu OTP còn có giới hạn riêng theo tài khoản.</p></main></html>"); return;
        }
        if(path.equals("/dang-nhap")) {
            String username=request.getParameter("username"),password=request.getParameter("password");
            if(username==null || username.length()>50 || password==null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) {
                guard.loginFailed(username); response.sendRedirect(request.getContextPath()+"/dang-nhap?error"); return;
            }
        }
        chain.doFilter(request,response);
    }
}
