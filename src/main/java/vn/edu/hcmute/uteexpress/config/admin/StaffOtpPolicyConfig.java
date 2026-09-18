package vn.edu.hcmute.uteexpress.config.admin;

import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.admin.AdminUserRepository;
import vn.edu.hcmute.uteexpress.dto.PasswordResetRequest;

/** Chỉ chặn OTP của cấp quản trị; luồng OTP khách hàng vẫn giữ nguyên. */
@Configuration
public class StaffOtpPolicyConfig implements WebMvcConfigurer {
    private final AdminUserRepository users;
    public StaffOtpPolicyConfig(AdminUserRepository users) { this.users = users; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if (!"POST".equals(request.getMethod())) { return true; }
                String username = request.getParameter("username");
                String email = request.getParameter("email");
                boolean activation = request.getRequestURI().endsWith("/xac-thuc-otp");
                var found = activation && username != null ? users.findByUsername(username)
                        : !activation && email != null ? users.findByEmailIgnoreCase(email.strip())
                        : java.util.Optional.<AppUser>empty();
                if (found.isPresent() && (found.get().getRole() == AppUser.Role.ADMIN
                        || found.get().getRole() == AppUser.Role.MANAGER)) {
                    // Cùng phản hồi với luồng công khai để không tiết lộ email của nhân sự nội bộ.
                    ModelAndView view;
                    if (activation) {
                        view = new ModelAndView("auth/verify-otp");
                        view.addObject("username", username);
                        view.addObject("error", "Mã OTP không đúng hoặc đã hết hạn.");
                    } else {
                        PasswordResetRequest form = new PasswordResetRequest();
                        form.setEmail(email);
                        view = new ModelAndView("auth/reset-password");
                        view.addObject("form", form);
                        if (request.getRequestURI().endsWith("/quen-mat-khau")) {
                            view.addObject("otpJustSent", true);
                        } else {
                            view.addObject("error", "Mã OTP không đúng hoặc đã hết hạn. Vui lòng gửi lại mã mới.");
                        }
                    }
                    throw new ModelAndViewDefiningException(view);
                }
                return true;
            }
        }).addPathPatterns("/xac-thuc-otp", "/quen-mat-khau", "/dat-lai-mat-khau");
    }
}
