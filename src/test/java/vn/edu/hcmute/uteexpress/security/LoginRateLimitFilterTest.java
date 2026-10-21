package vn.edu.hcmute.uteexpress.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

/** Chặn dò mật khẩu/OTP theo IP: chỉ đếm lần đăng nhập sai, IP khác không bị ảnh hưởng. */
class LoginRateLimitFilterTest {
    private final LoginRateLimitFilter filter = new LoginRateLimitFilter(true, 2, 3, 10);

    @Test void blocksLoginAfterTooManyFailuresFromSameIpOnly() throws Exception {
        assertEquals("/dang-nhap?error", login("10.0.0.1", false).getRedirectedUrl());
        assertEquals("/dang-nhap?error", login("10.0.0.1", false).getRedirectedUrl());
        var blocked = login("10.0.0.1", true);
        assertEquals("/dang-nhap?too-many", blocked.getRedirectedUrl());
        assertEquals("/trang-chu", login("10.0.0.2", true).getRedirectedUrl());
    }

    @Test void successfulLoginsAreNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) assertEquals("/trang-chu", login("10.0.0.3", true).getRedirectedUrl());
    }

    @Test void otpRequestsAreLimitedPerPath() throws Exception {
        for (int i = 0; i < 3; i++) assertEquals(200, post("/xac-thuc-otp", "10.0.0.4").getStatus());
        assertEquals(429, post("/xac-thuc-otp", "10.0.0.4").getStatus());
        assertEquals(200, post("/quen-mat-khau", "10.0.0.4").getStatus());
    }

    @Test void otherPagesAndDisabledFilterPassThrough() throws Exception {
        for (int i = 0; i < 10; i++) assertEquals(200, post("/nguoi-dung/tao-don", "10.0.0.5").getStatus());
        var off = new LoginRateLimitFilter(false, 1, 1, 10);
        for (int i = 0; i < 5; i++) {
            var request = new MockHttpServletRequest("POST", "/xac-thuc-otp"); request.setRemoteAddr("10.0.0.6");
            var response = new MockHttpServletResponse();
            off.doFilter(request, response, (req, res) -> { });
            assertEquals(200, response.getStatus());
        }
    }

    private MockHttpServletResponse login(String ip, boolean success) throws Exception {
        var request = new MockHttpServletRequest("POST", "/dang-nhap"); request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) ->
                ((MockHttpServletResponse) res).sendRedirect(success ? "/trang-chu" : "/dang-nhap?error"));
        return response;
    }

    private MockHttpServletResponse post(String path, String ip) throws Exception {
        var request = new MockHttpServletRequest("POST", path); request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        return response;
    }
}
