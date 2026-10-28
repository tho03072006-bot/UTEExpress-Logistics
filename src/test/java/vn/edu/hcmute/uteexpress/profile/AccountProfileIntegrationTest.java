package vn.edu.hcmute.uteexpress.profile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.UserProfileDetail;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.UserProfileDetailRepository;
import vn.edu.hcmute.uteexpress.service.profile.AccountProfileService;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Trang "Tài khoản của tôi": ảnh đại diện, thông tin cá nhân, tùy chọn email, trang Shipper; chạy trên H2 riêng. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:account-profile-test;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload.dir=target/test-proof-uploads", "app.mail.order-updates.enabled=false"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("isolated-logistics")
@Transactional
class AccountProfileIntegrationTest {
    private static final String USER_BASE = "/nguoi-dung/tai-khoan";
    private static final String SHIPPER_BASE = "/shipper/tai-khoan";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired UserProfileDetailRepository details;
    @Autowired AccountProfileService profiles;
    AppUser customer, shipper;

    @BeforeEach void seed() {
        customer = account("customer-profile", AppUser.Role.USER);
        shipper = account("shipper-profile", AppUser.Role.SHIPPER);
    }

    @Test void customerSeesProfileAndSavesPersonalDetails() throws Exception {
        mvc.perform(get(USER_BASE).servletPath(USER_BASE).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thông tin cá nhân")))
                .andExpect(content().string(containsString("Email cập nhật vận đơn")))
                .andExpect(content().string(containsString("Địa chỉ đã lưu")));

        String path = USER_BASE + "/ca-nhan";
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf())
                        .param("birthDate", "2001-05-20").param("gender", "FEMALE").param("province", "  Cần Thơ ")
                        .param("addressLine", "12 Nguyễn Văn Cừ").param("bio", "Bán đồ handmade"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(USER_BASE));
        UserProfileDetail saved = details.findByUser(customer).orElseThrow();
        assertEquals(LocalDate.of(2001, 5, 20), saved.getBirthDate());
        assertEquals(UserProfileDetail.Gender.FEMALE, saved.getGender());
        assertEquals("Cần Thơ", saved.getProvince());
        assertEquals("Bán đồ handmade", saved.getBio());
    }

    @Test void rejectsBirthDateBefore1900AndOverlongBio() throws Exception {
        String path = USER_BASE + "/ca-nhan";
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf())
                        .param("birthDate", "1850-01-01"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Ngày sinh phải từ năm 1900 đến hôm nay.")));
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf())
                        .param("bio", "x".repeat(161)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Giới thiệu tối đa 160 ký tự")));
        assertTrue(details.findByUser(customer).isEmpty());
    }

    @Test void avatarUploadServesCachedImageAndCanBeRemoved() throws Exception {
        String upload = USER_BASE + "/anh-dai-dien";
        mvc.perform(multipart(upload).file(new MockMultipartFile("avatar", "me.png", "image/png", png()))
                        .servletPath(upload).with(user(customer.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("message", "Đã cập nhật ảnh đại diện."));
        assertTrue(details.findByUser(customer).orElseThrow().getAvatarAsset().startsWith("cld1:"));

        var image = mvc.perform(get(upload).servletPath(upload).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", containsString("private"))).andReturn();
        String etag = image.getResponse().getHeader("ETag");
        mvc.perform(get(upload).servletPath(upload).header("If-None-Match", etag).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isNotModified());

        String remove = upload + "/xoa";
        mvc.perform(post(remove).servletPath(remove).with(user(customer.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertNull(details.findByUser(customer).orElseThrow().getAvatarAsset());
        mvc.perform(get(upload).servletPath(upload).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test void rejectsNonImageAvatar() throws Exception {
        String upload = USER_BASE + "/anh-dai-dien";
        mvc.perform(multipart(upload).file(new MockMultipartFile("avatar", "me.png", "image/png", new byte[]{1, 2, 3}))
                        .servletPath(upload).with(user(customer.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertTrue(details.findByUser(customer).isEmpty());
    }

    @Test void orderEmailPreferenceDefaultsOnAndCanBeTurnedOff() throws Exception {
        assertTrue(profiles.wantsOrderEmail(customer.getEmail()));
        String path = USER_BASE + "/thong-bao";
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(profiles.wantsOrderEmail(customer.getEmail().toUpperCase()));
        mvc.perform(post(path).servletPath(path).param("notifyOrderEmail", "true")
                        .with(user(customer.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(profiles.wantsOrderEmail(customer.getEmail()));
    }

    @Test void shipperHasOwnAccountPageAndCannotChangeEmail() throws Exception {
        mvc.perform(get(SHIPPER_BASE).servletPath(SHIPPER_BASE).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tỷ lệ giao thành công")))
                .andExpect(content().string(containsString("Hiện thông báo nổi khi có đơn mới")));

        String path = SHIPPER_BASE + "/cap-nhat";
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf())
                        .param("fullName", "Trần Văn Giao").param("phone", "0912345678").param("email", "hacker@example.com"))
                .andExpect(status().is3xxRedirection());
        AppUser reloaded = users.findByUsername(shipper.getUsername()).orElseThrow();
        assertEquals("Trần Văn Giao", reloaded.getFullName());
        assertEquals("0912345678", reloaded.getPhone());
        assertEquals("shipper-profile@test.invalid", reloaded.getEmail());

        String password = SHIPPER_BASE + "/doi-mat-khau";
        mvc.perform(get(password).servletPath(password).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("action=\"/shipper/tai-khoan/doi-mat-khau\"")));
    }

    @Test void shipperSeesAverageRatingFromServiceReviewsOfDeliveredOrders() throws Exception {
        mvc.perform(get(SHIPPER_BASE).servletPath(SHIPPER_BASE).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Chưa có lượt đánh giá nào.")));
        review(delivered("UTE-RATE-01"), 5, "Giao rất nhanh, gọi trước khi tới, gói hàng còn nguyên vẹn và sạch sẽ.");
        review(delivered("UTE-RATE-02"), 4, "Shipper thân thiện, giao đúng giờ hẹn, hướng dẫn thanh toán rõ ràng dễ hiểu.");
        var view = profiles.view(shipper.getUsername());
        assertEquals(2, view.rating().count());
        assertEquals(new java.math.BigDecimal("4.5"), view.rating().average());
        assertEquals("UTE-RATE-02", view.rating().latest().get(0).trackingCode());
        mvc.perform(get(SHIPPER_BASE).servletPath(SHIPPER_BASE).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("2 lượt đánh giá")))
                .andExpect(content().string(containsString("UTE-RATE-01")));
        assertNull(profiles.view(customer.getUsername()).rating());
    }

    @Autowired vn.edu.hcmute.uteexpress.repository.OrderRepository orders;
    @Autowired vn.edu.hcmute.uteexpress.repository.ServiceReviewRepository reviews;

    private vn.edu.hcmute.uteexpress.entity.Order delivered(String code) {
        var order = new vn.edu.hcmute.uteexpress.entity.Order(); order.setTrackingCode(code); order.setSender(customer); order.setShipper(shipper);
        order.setSenderName("Người gửi"); order.setReceiverName("Người nhận"); order.setReceiverPhone("0901234567");
        order.setSenderAddress("1 Võ Văn Ngân"); order.setReceiverAddress("2 Lê Văn Việt"); order.setWeightKg(1.0);
        order.setShippingFee(new java.math.BigDecimal("18000")); order.setStatus(vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.DELIVERED);
        order.setCreatedAt(java.time.LocalDateTime.now().minusDays(1)); order.setUpdatedAt(java.time.LocalDateTime.now().minusDays(1));
        return orders.saveAndFlush(order);
    }

    private void review(vn.edu.hcmute.uteexpress.entity.Order order, int stars, String content) {
        var review = new vn.edu.hcmute.uteexpress.entity.ServiceReview(); review.setOrder(order); review.setUser(customer);
        review.setRating(stars); review.setContent(content);
        review.setCreatedAt(java.time.LocalDateTime.now().plusSeconds(order.getId())); review.setUpdatedAt(review.getCreatedAt());
        reviews.saveAndFlush(review);
    }

    @Test void eachRoleOnlyReachesItsOwnAccountPage() throws Exception {
        mvc.perform(get(USER_BASE).servletPath(USER_BASE).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(SHIPPER_BASE).servletPath(SHIPPER_BASE).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private AppUser account(String username, AppUser.Role role) {
        var user = new AppUser(username, "test-hash", username + "@test.invalid");
        user.setRole(role); user.setEnabled(true); user.setFullName(username); user.setPhone("0901234567");
        return users.saveAndFlush(user);
    }

    private static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
