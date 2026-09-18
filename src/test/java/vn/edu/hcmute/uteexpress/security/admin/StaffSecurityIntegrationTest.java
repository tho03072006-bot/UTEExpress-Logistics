package vn.edu.hcmute.uteexpress.security.admin;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.uteexpress.config.SecurityConfig;
import vn.edu.hcmute.uteexpress.config.TimeConfig;
import vn.edu.hcmute.uteexpress.config.admin.*;
import vn.edu.hcmute.uteexpress.controller.admin.*;
import vn.edu.hcmute.uteexpress.controller.auth.AuthController;
import vn.edu.hcmute.uteexpress.controller.manager.*;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.service.AuthService;
import vn.edu.hcmute.uteexpress.service.OrderDraftService;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccountService;
import vn.edu.hcmute.uteexpress.service.manager.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AdminController.class, ManagerController.class, StaffAccountController.class,
        StaffAccessController.class, ManagerOrderController.class, AuthController.class})
@Import({SecurityConfig.class, StaffSecurityConfig.class, StaffOtpPolicyConfig.class,
        TimeConfig.class, StaffLoginAttemptService.class, StaffExceptionHandler.class})
class StaffSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired StaffLoginAttemptService attempts;
    @MockBean StaffAccountRepository accounts;
    @MockBean AdminUserRepository users;
    @MockBean StaffAccountService staffService;
    @MockBean ManagerDashboardService dashboard;
    @MockBean ManagerOrderService orders;
    @MockBean AuthService authService;
    @MockBean OrderDraftService drafts;
    @MockBean UserDetailsService sharedUserDetails;
    private StaffAccount admin;
    private StaffAccount manager;

    @BeforeEach void setUp() {
        admin = account(1L, "admin01", AppUser.Role.ADMIN);
        manager = account(2L, "manager01", AppUser.Role.MANAGER);
        when(accounts.findByUserUsername("admin01")).thenReturn(Optional.of(admin));
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.of(manager));
        when(dashboard.getSummary()).thenReturn(new ManagerDashboardResponse(4, 8, 2, 3));
        when(staffService.findAccounts(anyString(), anyInt())).thenReturn(new PageImpl<>(
                List.of(StaffAccountResponse.from(admin), StaffAccountResponse.from(manager))));
        when(orders.findOrders(anyString(), any(), any(), any(), anyInt())).thenReturn(new PageImpl<>(List.of(
                new ManagerOrderResponse("UTE123", "Người gửi", "Người nhận", "Chưa phân công",
                        Order.OrderStatus.PENDING_PICKUP, new BigDecimal("30000"), LocalDateTime.of(2026, 9, 18, 9, 0)))));
        attempts.clearFailures("admin01"); attempts.clearFailures("manager01");
    }

    @Test void anonymousMustUseInternalLogin() throws Exception {
        mvc.perform(get("/admin/trang-chu")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/noi-bo/dang-nhap"));
        mvc.perform(get("/manager/trang-chu")).andExpect(status().is3xxRedirection());
    }

    @Test void publicAdminPrincipalIsNotAnIssuedInternalAccount() throws Exception {
        mvc.perform(get("/admin/trang-chu").with(user("legacy-admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test void customerAndShipperCannotAccessInternalFunctions() throws Exception {
        for (String role : List.of("USER", "SHIPPER")) {
            mvc.perform(get("/manager/don-hang").with(user("external").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(orders);
    }

    @Test void managerCannotOpenOrPostAdminFunctions() throws Exception {
        mvc.perform(get("/admin/tai-khoan").with(authentication(auth(manager)))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/tai-khoan/cap-moi").with(authentication(auth(manager))).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(staffService);
    }

    @Test void adminInheritsManagerAccessAndRendersBothDashboards() throws Exception {
        mvc.perform(get("/admin/trang-chu").with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(view().name("admin/dashboard"))
                .andExpect(content().string(containsString("Quản trị và kiểm soát truy cập")));
        mvc.perform(get("/manager/trang-chu").with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(view().name("manager/dashboard"));
    }

    @Test void managerDashboardDoesNotShowAdminAccountLink() throws Exception {
        mvc.perform(get("/manager/trang-chu").with(authentication(auth(manager))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/admin/tai-khoan\""))));
    }

    @Test void accountListRendersVersionsAndCsrfWithoutPasswordHashes() throws Exception {
        mvc.perform(get("/admin/tai-khoan").with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("manager01")))
                .andExpect(content().string(containsString("name=\"revision\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString(admin.getUser().getPassword()))));
    }

    @Test void accountCreatePageRenders() throws Exception {
        mvc.perform(get("/admin/tai-khoan/cap-moi").with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(view().name("admin/account-create"));
    }

    @Test void loginPageRendersCsrfAndNoOtpForm() throws Exception {
        mvc.perform(get("/noi-bo/dang-nhap")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("action=\"/xac-thuc-otp\""))));
    }

    @Test void internalLoginRejectsMissingCsrf() throws Exception {
        mvc.perform(post("/noi-bo/dang-nhap").param("username", "admin01").param("password", "OriginalPass@2026"))
                .andExpect(status().isForbidden());
    }

    @Test void internalLoginRedirectsByRole() throws Exception {
        mvc.perform(post("/noi-bo/dang-nhap").with(csrf()).param("username", "admin01")
                .param("password", "OriginalPass@2026")).andExpect(redirectedUrl("/admin/trang-chu"));
        mvc.perform(post("/noi-bo/dang-nhap").with(csrf()).param("username", "manager01")
                .param("password", "OriginalPass@2026")).andExpect(redirectedUrl("/manager/trang-chu"));
    }

    @Test void disabledOrNonIssuedAccountCannotLogin() throws Exception {
        admin.setActive(false);
        mvc.perform(post("/noi-bo/dang-nhap").with(csrf()).param("username", "admin01")
                .param("password", "OriginalPass@2026")).andExpect(redirectedUrl("/noi-bo/dang-nhap?error"));
        mvc.perform(post("/noi-bo/dang-nhap").with(csrf()).param("username", "legacyadmin")
                .param("password", "OriginalPass@2026")).andExpect(redirectedUrl("/noi-bo/dang-nhap?error"));
    }

    @Test void blocksCorrectPasswordAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) { attempts.recordFailure("admin01"); }
        mvc.perform(post("/noi-bo/dang-nhap").with(csrf()).param("username", "admin01")
                .param("password", "OriginalPass@2026")).andExpect(redirectedUrl("/noi-bo/dang-nhap?error"));
    }

    @Test void mutationRequiresCsrfEvenForAdmin() throws Exception {
        mvc.perform(post("/admin/tai-khoan/2/cap-nhat").with(authentication(auth(admin)))
                .param("revision", "0").param("role", "ADMIN").param("active", "true"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(staffService);
    }

    @Test void revokedSessionCannotReadDashboard() throws Exception {
        var old = auth(admin);
        admin.revokeSessions();
        mvc.perform(get("/admin/trang-chu").with(authentication(old)))
                .andExpect(redirectedUrl("/noi-bo/dang-nhap?expired"));
        verifyNoInteractions(dashboard);
    }

    @Test void disabledFlagCannotBeBypassedByExistingSession() throws Exception {
        var old = auth(admin);
        admin.setActive(false);
        mvc.perform(get("/admin/trang-chu").with(authentication(old)))
                .andExpect(redirectedUrl("/noi-bo/dang-nhap?expired"));
    }

    @Test void sharedPasswordChangeRevokesInternalSession() throws Exception {
        var old = auth(admin);
        admin.getUser().setPassword(encoder.encode("ChangedElsewhere@2026"));
        mvc.perform(get("/admin/trang-chu").with(authentication(old)))
                .andExpect(redirectedUrl("/noi-bo/dang-nhap?expired"));
    }

    @Test void changedRoleInvalidatesPreviousAuthorities() throws Exception {
        var old = auth(admin);
        admin.getUser().setRole(AppUser.Role.MANAGER);
        mvc.perform(get("/admin/trang-chu").with(authentication(old)))
                .andExpect(redirectedUrl("/noi-bo/dang-nhap?expired"));
    }

    @Test void temporaryPasswordCannotOpenDashboardOrPostAdminChanges() throws Exception {
        admin.setPasswordChangeRequired(true);
        mvc.perform(get("/admin/trang-chu").with(authentication(auth(admin))))
                .andExpect(redirectedUrl("/noi-bo/doi-mat-khau"));
        mvc.perform(post("/admin/tai-khoan/cap-moi").with(authentication(auth(admin))).with(csrf()))
                .andExpect(redirectedUrl("/noi-bo/doi-mat-khau"));
        verifyNoInteractions(staffService);
        mvc.perform(get("/noi-bo/doi-mat-khau").with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(view().name("admin/password-change"));
    }

    @Test void invalidCreateDoesNotCallServiceOrReflectPassword() throws Exception {
        mvc.perform(post("/admin/tai-khoan/cap-moi").with(authentication(auth(admin))).with(csrf())
                .param("username", "x").param("email", "bad").param("role", "USER")
                .param("password", "SECRET_TO_NOT_ECHO").param("confirmPassword", "other"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("form"))
                .andExpect(content().string(not(containsString("SECRET_TO_NOT_ECHO"))));
        verifyNoInteractions(staffService);
    }

    @Test void validCreateRedirectsAndUsesAuthenticatedActor() throws Exception {
        mvc.perform(post("/admin/tai-khoan/cap-moi").with(authentication(auth(admin))).with(csrf())
                .param("username", "manager02").param("fullName", "Nguyễn Văn Thắng")
                .param("email", "new@example.com").param("role", "MANAGER")
                .param("password", "StrongPassword@2026").param("confirmPassword", "StrongPassword@2026")
                .param("actor", "spoofed")).andExpect(redirectedUrl("/admin/tai-khoan"));
        verify(staffService).createAccount(eq("admin01"), any());
    }

    @Test void invalidRoleAndMissingRevisionDoNotCallUpdate() throws Exception {
        mvc.perform(post("/admin/tai-khoan/2/cap-nhat").with(authentication(auth(admin))).with(csrf())
                .param("role", "SHIPPER").param("active", "true"))
                .andExpect(redirectedUrl("/admin/tai-khoan")).andExpect(flash().attributeExists("error"));
        verifyNoInteractions(staffService);
    }

    @Test void ordersPageRendersRowsAndFilters() throws Exception {
        mvc.perform(get("/manager/don-hang").with(authentication(auth(manager))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("UTE123")))
                .andExpect(content().string(containsString("30.000 đ")));
    }

    @Test void invalidDateAndStatusReturnFriendly400() throws Exception {
        mvc.perform(get("/manager/don-hang").param("from", "invalid").with(authentication(auth(manager))))
                .andExpect(status().isBadRequest()).andExpect(view().name("admin/error"));
        mvc.perform(get("/manager/don-hang").param("status", "UNKNOWN").with(authentication(auth(manager))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orders);
    }

    @Test void serverFailureDoesNotLeakSqlOrSecrets() throws Exception {
        when(dashboard.getSummary()).thenThrow(new IllegalStateException("jdbc:SECRET_DATABASE_PASSWORD"));
        mvc.perform(get("/manager/trang-chu").with(authentication(auth(manager))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("SECRET_DATABASE_PASSWORD"))));
    }

    @Test void privilegedOtpActivationIsBlockedWithoutCallingSharedService() throws Exception {
        when(users.findByUsername("admin01")).thenReturn(Optional.of(admin.getUser()));
        mvc.perform(post("/xac-thuc-otp").param("username", "admin01").param("otp", "123456"))
                .andExpect(status().isOk()).andExpect(view().name("auth/verify-otp"))
                .andExpect(model().attributeExists("error"));
        verifyNoInteractions(authService);
    }

    @Test void privilegedPublicPasswordRecoveryIsBlocked() throws Exception {
        when(users.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(manager.getUser()));
        mvc.perform(post("/quen-mat-khau").param("email", "manager@example.com"))
                .andExpect(status().isOk()).andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("otpJustSent", true));
        mvc.perform(post("/dat-lai-mat-khau").param("email", "manager@example.com"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        verifyNoInteractions(authService);
    }

    @Test void forgedUsernameCannotBypassPrivilegedEmailPolicy() throws Exception {
        when(users.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(manager.getUser()));
        mvc.perform(post("/quen-mat-khau").param("email", "manager@example.com").param("username", "customer"))
                .andExpect(status().isOk()).andExpect(view().name("auth/reset-password"));
        verifyNoInteractions(authService);
    }

    @Test void databaseFailureFailsClosedWithoutExposingDetails() throws Exception {
        when(accounts.findByUserUsername("admin01")).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("SECRET_DB_URL"));
        mvc.perform(get("/admin/trang-chu").with(authentication(auth(admin))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("SECRET_DB_URL"))));
        verifyNoInteractions(dashboard);
    }

    @Test void successfulPasswordChangeEndsCurrentSession() throws Exception {
        mvc.perform(post("/noi-bo/doi-mat-khau").with(authentication(auth(manager))).with(csrf())
                .param("currentPassword", "OriginalPass@2026").param("password", "StrongPassword@2027")
                .param("confirmPassword", "StrongPassword@2027"))
                .andExpect(redirectedUrl("/noi-bo/dang-nhap?changed"));
        verify(staffService).changePassword(eq("manager01"), any());
    }

    @Test void validUpdateAndResetUseActorFromSession() throws Exception {
        mvc.perform(post("/admin/tai-khoan/2/cap-nhat").with(authentication(auth(admin))).with(csrf())
                .param("revision", "0").param("active", "false").param("role", "MANAGER"))
                .andExpect(redirectedUrl("/admin/tai-khoan"));
        verify(staffService).updateAccount(eq("admin01"), eq(2L), any());
        mvc.perform(post("/admin/tai-khoan/2/cap-lai-mat-khau").with(authentication(auth(admin))).with(csrf())
                .param("revision", "0").param("password", "StrongPassword@2027")
                .param("confirmPassword", "StrongPassword@2027"))
                .andExpect(redirectedUrl("/admin/tai-khoan"));
        verify(staffService).resetPassword(eq("admin01"), eq(2L), eq(0L), any());
    }

    @Test void ordersEmptyStateRenders() throws Exception {
        when(orders.findOrders(anyString(), any(), any(), any(), anyInt())).thenReturn(Page.empty());
        mvc.perform(get("/manager/don-hang").with(authentication(auth(manager))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Không có đơn phù hợp")));
    }

    @Test void customerOtpFlowRemainsUnchanged() throws Exception {
        var customer = new AppUser("customer", "hash", "customer@example.com");
        when(users.findByUsername("customer")).thenReturn(Optional.of(customer));
        when(authService.verifyOtp("customer", "123456")).thenReturn(true);
        mvc.perform(post("/xac-thuc-otp").param("username", "customer").param("otp", "123456"))
                .andExpect(status().isOk()).andExpect(view().name("auth/login"));
        verify(authService).verifyOtp("customer", "123456");
    }

    private StaffAccount account(Long id, String name, AppUser.Role role) {
        AppUser user = new AppUser(name, encoder.encode("OriginalPass@2026"), name + "@example.com");
        user.setRole(role); user.setEnabled(true); user.setFullName("Nhân sự thử nghiệm");
        StaffAccount account = new StaffAccount();
        account.setId(id); account.setUser(user); account.setIssuedBy("operator"); account.setPasswordChangeRequired(false);
        return account;
    }
    private Authentication auth(StaffAccount account) {
        var principal = new StaffPrincipal(account);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
