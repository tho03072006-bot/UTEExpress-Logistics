package vn.edu.hcmute.uteexpress.security;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.uteexpress.config.SecurityConfig;
import vn.edu.hcmute.uteexpress.config.WebSocketConfig;
import vn.edu.hcmute.uteexpress.controller.auth.AuthController;
import vn.edu.hcmute.uteexpress.controller.shipper.ShipperController;
import vn.edu.hcmute.uteexpress.controller.user.UserOrderController;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.admin.AdminUserRepository;
import vn.edu.hcmute.uteexpress.service.AuthService;
import vn.edu.hcmute.uteexpress.service.OrderDraftService;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.SavedAddressService;
import vn.edu.hcmute.uteexpress.service.ServiceReviewService;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofService;
import vn.edu.hcmute.uteexpress.service.tracking.ShipperStatisticsService;
import vn.edu.hcmute.uteexpress.service.tracking.TrackingService;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UserOrderController.class, ShipperController.class})
@Import({SecurityConfig.class, WebSocketConfig.class})
class PublicSessionSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockBean UserDetailsService users;
    @MockBean AuthService authService;
    @MockBean OrderService orders;
    @MockBean SavedAddressService addresses;
    @MockBean OrderDraftService drafts;
    @MockBean OrderPaymentService payments;
    @MockBean ServiceReviewService reviews;
    @MockBean TrackingService tracking;
    @MockBean DeliveryProofService proofs;
    @MockBean ShipperStatisticsService statistics;
    // The MVC slice also discovers the internal-account OTP policy.
    @MockBean AdminUserRepository adminUsers;

    @Test void loginAndOtpFormsRenderCsrfTokens() throws Exception {
        mvc.perform(get("/dang-nhap")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/quen-mat-khau")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test void loginRejectsMissingOrInvalidCsrfBeforeCheckingCredentials() throws Exception {
        mvc.perform(post("/dang-nhap").param("username", "customer").param("password", "test-password"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/dang-nhap").with(csrf().useInvalidToken())
                        .param("username", "customer").param("password", "test-password"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test void loginWithCsrfStillAuthenticates() throws Exception {
        when(users.loadUserByUsername("customer")).thenReturn(User.withUsername("customer")
                .password(encoder.encode("test-password")).roles("USER").build());
        mvc.perform(post("/dang-nhap").with(csrf()).param("username", "customer")
                        .param("password", "test-password"))
                .andExpect(redirectedUrl("/nguoi-dung/trang-chu"));
    }

    @Test void publicOtpMutationRequiresCsrf() throws Exception {
        mvc.perform(post("/xac-thuc-otp").param("username", "customer").param("otp", "123456"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/quen-mat-khau").param("email", "customer@example.com"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test void customerCannotCancelWithoutValidCsrf() throws Exception {
        mvc.perform(post("/nguoi-dung/don-hang/7/huy").with(user("customer")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/nguoi-dung/don-hang/7/huy").with(user("customer"))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orders);
    }

    @Test void tokenRenderedInCustomerFormCanBeSubmittedWithItsSession() throws Exception {
        Order order = order();
        when(orders.findOwnedOrder(7L, "customer")).thenReturn(order);
        when(orders.buildTimeline(order)).thenReturn(List.of());
        when(payments.findByOrder(order)).thenReturn(Optional.empty());
        when(reviews.findByOrderId(7L)).thenReturn(Optional.empty());

        var result = mvc.perform(get("/nguoi-dung/don-hang/7").with(user("customer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/nguoi-dung/don-hang/7/huy\"")))
                .andReturn();
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(result.getResponse().getContentAsString());
        assertTrue(token.find(), "Thymeleaf must render a CSRF token in POST forms");
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);

        mvc.perform(post("/nguoi-dung/don-hang/7/huy").session(session).with(user("customer"))
                        .param("_csrf", token.group(1)))
                .andExpect(redirectedUrl("/nguoi-dung/don-hang"));
        verify(orders).cancelOrder(7L, "customer");
    }

    @Test void shipperStatusChangeRequiresCsrfAndKeepsRoleRestriction() throws Exception {
        mvc.perform(post("/shipper/don/7/trang-thai").with(user("shipper").roles("SHIPPER"))
                        .param("status", "PICKED_UP"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/shipper/don/7/trang-thai").with(user("customer")).with(csrf())
                        .param("status", "PICKED_UP"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tracking);

        mvc.perform(post("/shipper/don/7/trang-thai").with(user("shipper").roles("SHIPPER"))
                        .with(csrf()).param("status", "PICKED_UP"))
                .andExpect(redirectedUrl("/shipper/don/7"));
        verify(tracking).updateStatus(7L, "shipper", Order.OrderStatus.PICKED_UP);
    }

    @Test void shipperProofUploadRequiresCsrf() throws Exception {
        var image = new MockMultipartFile("proofImage", "proof.png", "image/png", new byte[] {1});
        mvc.perform(multipart("/shipper/don/7/bang-chung").file(image)
                        .with(user("shipper").roles("SHIPPER")).param("signatureData", "test-signature"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(proofs);

        mvc.perform(multipart("/shipper/don/7/bang-chung").file(image)
                        .with(user("shipper").roles("SHIPPER")).with(csrf())
                        .param("signatureData", "test-signature"))
                .andExpect(redirectedUrl("/shipper/don/7"));
        verify(proofs).saveProof(eq(7L), eq("shipper"), any(), eq("test-signature"));
    }

    @Test void shipperStatusFormRendersCsrfToken() throws Exception {
        when(tracking.findAssignedOrder(7L, "shipper")).thenReturn(Optional.of(order()));
        mvc.perform(get("/shipper/don/7").with(user("shipper").roles("SHIPPER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/shipper/don/7/trang-thai\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test void logoutRequiresPostWithCsrf() throws Exception {
        mvc.perform(post("/logout").with(user("customer"))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("customer")).with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    @Test void authenticatedSockJsXhrTransportWorksWithoutHttpCsrfToken() throws Exception {
        String transport = "/ws-tracking/000/csrf-session/xhr";
        mvc.perform(post(transport).servletPath(transport).with(user("shipper").roles("SHIPPER"))
                        .header("Origin", "http://localhost"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("o\n")));

        String send = "/ws-tracking/000/csrf-session/xhr_send";
        mvc.perform(post(send).servletPath(send).with(user("shipper").roles("SHIPPER"))
                        .header("Origin", "http://localhost")
                        .contentType("application/json").content("[]"))
                .andExpect(status().isNoContent());
    }

    @Test void sockJsTransportExemptionDoesNotAllowAnonymousAccess() throws Exception {
        String transport = "/ws-tracking/000/anonymous-session/xhr";
        mvc.perform(post(transport).servletPath(transport))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/dang-nhap"));
    }

    @Test void csrfExemptionDoesNotCoverApiOrOtherWebSocketPaths() throws Exception {
        mvc.perform(post("/api/tracking/test").with(user("customer")))
                .andExpect(status().isForbidden());
        String path = "/ws-tracking/000/session/change-order";
        mvc.perform(post(path).servletPath(path).with(user("customer")))
                .andExpect(status().isForbidden());
    }

    private Order order() {
        Order order = new Order();
        order.setId(7L);
        order.setTrackingCode("UTE-CSRF-TEST");
        order.setWeightKg(1.0);
        order.setShippingFee(new BigDecimal("18000"));
        return order;
    }
}
