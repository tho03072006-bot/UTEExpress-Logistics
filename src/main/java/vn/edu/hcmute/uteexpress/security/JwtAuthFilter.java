package vn.edu.hcmute.uteexpress.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter doc header "Authorization: Bearer <token>" cho cac API dung JWT.
 * Con la stub, CHUA duoc dang ky trong SecurityConfig (filter chain hien tai
 * dang permitAll toan bo). TV3 se hoan thien + noi vao SecurityConfig khi lam
 * phan JWT o Tuan 5 theo ke hoach.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // TODO: doc header Authorization, validate token bang jwtTokenProvider,
        // set SecurityContextHolder neu hop le. Hien tai chi cho request di qua.
        filterChain.doFilter(request, response);
    }
}
