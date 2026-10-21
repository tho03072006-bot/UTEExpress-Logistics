package vn.edu.hcmute.uteexpress.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.SavedAddressRepository;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Quy tắc nhập liệu của các form phía khách hàng: đăng ký, đổi mật khẩu, hồ sơ, sổ địa chỉ. Chạy trên H2 riêng. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:form-validation-test;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload.dir=target/test-proof-uploads", "app.mail.order-updates.enabled=false"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("isolated-logistics")
@Transactional
class FormValidationIntegrationTest {
    private static final String CURRENT = "MatKhau@123";
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired SavedAddressRepository addresses;
    @Autowired PasswordEncoder encoder;
    AppUser customer;

    @BeforeEach void seed() {
        customer = new AppUser("form-customer", encoder.encode(CURRENT), "form-customer@test.invalid");
        customer.setRole(AppUser.Role.USER); customer.setEnabled(true); customer.setFullName("Trần Minh Khôi"); customer.setPhone("0901234567");
        customer = users.saveAndFlush(customer);
    }

    @Test void registrationRejectsBadUsernameNameEmailAndPassword() throws Exception {
        mvc.perform(post("/dang-ky").with(csrf()).param("fullName", "12345").param("username", "a b")
                        .param("email", "ban@localhost").param("password", "ờ".repeat(30)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Họ tên chỉ gồm chữ và khoảng trắng")))
                .andExpect(content().string(containsString("Tên đăng nhập 4–50 ký tự")))
                .andExpect(content().string(containsString("Email cần có tên miền hợp lệ")))
                .andExpect(content().string(containsString("Mật khẩu quá dài")));
        mvc.perform(post("/dang-ky").with(csrf()).param("fullName", " ").param("username", "x".repeat(60))
                        .param("email", "a@b.vn").param("password", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu từ 6 đến 64 ký tự")));
        assertTrue(users.findByUsername("a b").isEmpty());
    }

    @Test void changePasswordHandlesOverlongAndControlCharacters() throws Exception {
        String path = "/nguoi-dung/tai-khoan/doi-mat-khau";
        String tooLong = "Mật khẩu rất dài có dấu ".repeat(4);
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf())
                        .param("currentPassword", CURRENT).param("newPassword", tooLong).param("confirmPassword", tooLong))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Mật khẩu")));
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf())
                        .param("currentPassword", CURRENT).param("newPassword", "abc\u0000def").param("confirmPassword", "abc\u0000def"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("ký tự điều khiển")));
        assertTrue(encoder.matches(CURRENT, users.findByUsername(customer.getUsername()).orElseThrow().getPassword()));
    }

    @Test void profileRejectsNumericNameLandlineAndEmailWithoutDomain() throws Exception {
        String path = "/nguoi-dung/tai-khoan/cap-nhat";
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf())
                        .param("fullName", "<b>123</b>").param("email", "khoi@mail").param("phone", "0281234567"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Họ tên chỉ gồm chữ và khoảng trắng")))
                .andExpect(content().string(containsString("Email cần có tên miền hợp lệ")))
                .andExpect(content().string(containsString("Nhập số di động Việt Nam 10 số")));
        assertEquals("Trần Minh Khôi", users.findByUsername(customer.getUsername()).orElseThrow().getFullName());
    }

    @Test void savedAddressUsesSamePhoneRuleAsOrders() throws Exception {
        String path = "/nguoi-dung/dia-chi/them";
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf())
                        .param("label", "Nhà").param("contactName", "Trần Minh Khôi").param("contactPhone", "01234567890")
                        .param("addressLine", "12\u0007 Võ Văn Ngân").param("addressType", "SENDER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhập số di động Việt Nam 10 số")))
                .andExpect(content().string(containsString("Địa chỉ không được chứa ký tự điều khiển")));
        assertEquals(0, addresses.countByUser(customer));
    }
}
