package vn.edu.hcmute.uteexpress.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;

/**
 * Đăng nhập thật qua form, KHÔNG bọc @Transactional: giống lúc chạy app, Spring Security tìm tài khoản ngoài giao dịch.
 * Bắt lỗi kiểu "connection is closed" mà các test chạy trong giao dịch không thấy được. Chạy trên H2 riêng.
 */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:login-outside-tx;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload.dir=target/test-proof-uploads", "app.mail.order-updates.enabled=false"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("isolated-logistics")
class LoginOutsideTransactionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test void customerAndShipperCanLogInAndRepeatedLookupsReturnSameAccount() throws Exception {
        account("login-customer", AppUser.Role.USER);
        account("login-shipper", AppUser.Role.SHIPPER);
        mvc.perform(formLogin("/dang-nhap").user("login-customer").password("MatKhau@123"))
                .andExpect(authenticated().withUsername("login-customer"));
        mvc.perform(formLogin("/dang-nhap").user("login-shipper").password("MatKhau@123"))
                .andExpect(authenticated().withUsername("login-shipper"));
        mvc.perform(formLogin("/dang-nhap").user("login-shipper").password("sai-mat-khau")).andExpect(unauthenticated());
        // Ngoài request web: tìm lặp lại vẫn đúng, tên không tồn tại trả rỗng.
        assertEquals("login-customer", users.findByUsername("login-customer").orElseThrow().getUsername());
        assertEquals("login-customer", users.findByUsername("login-customer").orElseThrow().getUsername());
        assertTrue(users.findByUsername("khong-ton-tai").isEmpty());
        assertTrue(users.findByUsername(null).isEmpty());
    }

    private void account(String username, AppUser.Role role) {
        var user = new AppUser(username, encoder.encode("MatKhau@123"), username + "@test.invalid");
        user.setRole(role); user.setEnabled(true); user.setFullName("Nguyễn Văn Thử");
        users.saveAndFlush(user);
    }
}
