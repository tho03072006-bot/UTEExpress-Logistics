package vn.edu.hcmute.uteexpress.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Trang đăng nhập hiện số lần nhập sai: nội bộ "lần N/5" (khóa theo tài khoản), khách hàng "lần N/20" (chặn theo máy). */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:login-failure-count;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload.dir=target/test-proof-uploads", "app.mail.order-updates.enabled=false"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("isolated-logistics")
class LoginFailureCountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired PasswordEncoder encoder;

    @Test void staffLoginShowsAttemptNumberThenLockMessage() throws Exception {
        var manager = new AppUser("count-manager", encoder.encode("DungMatKhau@2026"), "count-manager@test.invalid");
        manager.setRole(AppUser.Role.MANAGER); manager.setEnabled(true); manager.setFullName("Lê Minh Quân");
        manager = users.saveAndFlush(manager);
        var account = new StaffAccount(); account.setUser(manager); account.setActive(true);
        account.setPasswordChangeRequired(false); account.setIssuedBy("test-only");
        staff.saveAndFlush(account);

        var session = new MockHttpSession();
        for (int attempt = 1; attempt <= 4; attempt++) {
            mvc.perform(post("/noi-bo/dang-nhap").session(session).with(csrf()).param("username", "count-manager").param("password", "sai"))
                    .andExpect(redirectedUrl("/noi-bo/dang-nhap?error"));
            mvc.perform(get("/noi-bo/dang-nhap").param("error", "").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("lần " + attempt + "/5")))
                    .andExpect(content().string(containsString("Còn " + (5 - attempt) + " lần")))
                    .andExpect(content().string(containsString("value=\"count-manager\"")));
        }
        mvc.perform(post("/noi-bo/dang-nhap").session(session).with(csrf()).param("username", "count-manager").param("password", "sai"))
                .andExpect(redirectedUrl("/noi-bo/dang-nhap?error&locked"));
        mvc.perform(get("/noi-bo/dang-nhap").param("error", "").param("locked", "").session(session))
                .andExpect(content().string(containsString("Tài khoản đang tạm khóa 15 phút")));
        // Flash chỉ dùng một lần: mở lại trang thì không còn hiện số lần cũ.
        mvc.perform(get("/noi-bo/dang-nhap").param("error", "").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("lần 4/5"))));
    }

    @Test void customerLoginShowsAttemptNumberAndClearsAfterSuccess() throws Exception {
        var customer = new AppUser("count-customer", encoder.encode("DungMatKhau@2026"), "count-customer@test.invalid");
        customer.setRole(AppUser.Role.USER); customer.setEnabled(true); customer.setFullName("Trần Minh Khôi");
        users.saveAndFlush(customer);
        var session = new MockHttpSession();
        // Bộ đếm tính theo máy (mọi trang đăng nhập), nên chỉ kiểm tra số lần tăng đúng 1 sau mỗi lần sai.
        int previous = -1;
        for (int attempt = 1; attempt <= 2; attempt++) {
            mvc.perform(post("/dang-nhap").session(session).with(csrf()).param("username", "count-customer").param("password", "sai"))
                    .andExpect(redirectedUrl("/dang-nhap?error"));
            String page = mvc.perform(get("/dang-nhap").param("error", "").session(session))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            var match = java.util.regex.Pattern.compile("lần (\\d+)/20; còn (\\d+) lần").matcher(page);
            org.junit.jupiter.api.Assertions.assertTrue(match.find(), "Trang đăng nhập phải hiện số lần sai");
            org.junit.jupiter.api.Assertions.assertTrue(page.contains("value=\"count-customer\""), "Phải giữ lại tên đã gõ");
            int count = Integer.parseInt(match.group(1));
            org.junit.jupiter.api.Assertions.assertEquals(20 - count, Integer.parseInt(match.group(2)));
            if (previous >= 0) org.junit.jupiter.api.Assertions.assertEquals(previous + 1, count);
            previous = count;
        }
        mvc.perform(post("/dang-nhap").session(session).with(csrf()).param("username", "count-customer").param("password", "DungMatKhau@2026"))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertNull(session.getAttribute(LoginRateLimitFilter.FAILURES_ATTRIBUTE));
    }
}
