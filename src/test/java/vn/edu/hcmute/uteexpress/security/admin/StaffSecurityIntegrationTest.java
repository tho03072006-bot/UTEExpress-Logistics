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
        StaffAccessController.class, AdminAccountController.class, ManagerOrderController.class, AuthController.class,
        ShippingTariffController.class, AuditController.class, DispatchController.class, ManagerPromotionController.class, ManagerReportController.class, ManagerTariffController.class,
        StaffApiAccessController.class, StaffProfileController.class, BatchTrackingController.class, BranchAdminController.class})
@Import({SecurityConfig.class, StaffSecurityConfig.class, StaffOtpPolicyConfig.class,
        TimeConfig.class, StaffLoginAttemptService.class, StaffExceptionHandler.class})
class StaffSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired StaffLoginAttemptService attempts;
    @MockBean StaffAccountRepository accounts;
    @MockBean AdminUserRepository users;
    @MockBean vn.edu.hcmute.uteexpress.repository.AppUserRepository publicUsers;
    @MockBean vn.edu.hcmute.uteexpress.service.admin.AdminAccountService allAccounts;
    @MockBean StaffAccountService staffService;
    @MockBean vn.edu.hcmute.uteexpress.service.routing.StaffBranchService branchAssignments;
    @MockBean vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService routingCatalog;
    @MockBean vn.edu.hcmute.uteexpress.service.routing.BranchAdminService branchAdmin;
    @MockBean ManagerDashboardService dashboard;
    @MockBean StaffDashboardChartService dashboardCharts;
    @MockBean StaffWorkbenchService workbench;
    @MockBean vn.edu.hcmute.uteexpress.service.admin.StaffApiAccessService apiAccess;
    @MockBean vn.edu.hcmute.uteexpress.security.JwtTokenProvider jwtTokens;
    @MockBean ManagerOrderService orders;
    @MockBean ManagerDispatchService dispatch;
    @MockBean vn.edu.hcmute.uteexpress.service.tracking.DispatchService sharedDispatch;
    @MockBean vn.edu.hcmute.uteexpress.service.routing.DispatchBulkService bulkDispatch;
    @MockBean vn.edu.hcmute.uteexpress.service.tracking.ShipperWorkspaceService sharedWorkspace;
    @MockBean vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofService deliveryProofs;
    @MockBean ManagerPromotionService promotions;
    @MockBean ManagerReportService reports;
    @MockBean vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService tariffs;
    @MockBean vn.edu.hcmute.uteexpress.service.admin.AuditService audit;
    @MockBean AuthService authService;
    @MockBean OrderDraftService drafts;
    @MockBean UserDetailsService sharedUserDetails;
    private StaffAccount admin;
    private StaffAccount manager;

    @BeforeEach void setUp() {
        when(branchAssignments.selection(anyString(), anyLong())).thenReturn(new StaffBranchRequest());
        when(branchAssignments.selectionByUsername(anyString(), anyString())).thenReturn(new StaffBranchRequest());
        var branch = new vn.edu.hcmute.uteexpress.entity.routing.Branch("SGN", "Kho SGN", "SGN", "Thủ Đức", "SGN-THUDUC", LocalDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(branch, "id", 1L);
        when(routingCatalog.branches()).thenReturn(List.of(branch));
        when(routingCatalog.areas()).thenReturn(List.of(new vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService.AreaOption("SGN-THUDUC", "Thủ Đức", "SGN", 1L, "SGN")));
        when(branchAdmin.list("admin01")).thenReturn(List.of(branch));
        when(branchAdmin.get("admin01", 1L)).thenReturn(branch);
        admin = account(1L, "admin01", AppUser.Role.ADMIN);
        manager = account(2L, "manager01", AppUser.Role.MANAGER);
        when(accounts.findByUserUsername("admin01")).thenReturn(Optional.of(admin));
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.of(manager));
        when(dashboard.getSummary()).thenReturn(new ManagerDashboardResponse(4, 8, 2, 3));
        when(dashboard.getSummary(anyString())).thenReturn(new ManagerDashboardResponse(4, 8, 2, 3));
        when(dashboardCharts.getAdminCharts(anyString())).thenReturn(chartFixture());
        when(dashboardCharts.getOperationsCharts(anyString())).thenReturn(chartFixture());
        when(workbench.getWorkQueues(anyString())).thenReturn(List.of(
            new WorkQueueResponse("Chưa có Shipper", 3, "/manager/canh-bao?alert=UNASSIGNED", "Kiểm tra và phân công."),
            new WorkQueueResponse("Chậm lấy hàng", 2, "/manager/canh-bao?alert=LATE_PICKUP", "Đối chiếu thời hạn lấy."),
            new WorkQueueResponse("Cần kiểm tra thanh toán", 1, "/manager/canh-bao?alert=PAYMENT_ISSUE", "Yêu cầu người gửi thanh toán."),
            new WorkQueueResponse("Chậm giao hàng", 4, "/manager/canh-bao?alert=LATE_DELIVERY", "Kiểm tra tiến độ.")));
        when(apiAccess.findAccess(anyString(), anyInt())).thenReturn(new PageImpl<>(List.of(StaffAccessResponse.from(admin), StaffAccessResponse.from(manager))));
        when(jwtTokens.isEnabled()).thenReturn(true);
        when(jwtTokens.getLifetimeSeconds()).thenReturn(1800L);
        when(staffService.findOwnProfile("admin01")).thenReturn(StaffProfileResponse.from(admin));
        when(staffService.findOwnProfile("manager01")).thenReturn(StaffProfileResponse.from(manager));
        when(audit.findEvents(anyString(), org.mockito.ArgumentMatchers.any(AuditFilterRequest.class), anyInt())).thenReturn(Page.empty());
        when(staffService.findAccounts(anyString(), anyInt())).thenReturn(new PageImpl<>(
                List.of(StaffAccountResponse.from(admin), StaffAccountResponse.from(manager))));
        var user = new AppUser("userdemo", "not-rendered-hash", "user@example.com");
        user.setId(3L); user.setEnabled(true); user.setFullName("Người dùng"); user.setPhone("0901234567");
        when(allAccounts.findAccounts(anyString(), any(), anyInt())).thenReturn(new PageImpl<>(
            List.of(AdminAccountResponse.from(user), AdminAccountResponse.from(admin.getUser()))));
        when(allAccounts.findAccount(anyString(), eq(3L))).thenReturn(AdminAccountResponse.from(user));
        when(orders.findOrders(anyString(), any(), anyString(), any(), any(), any(), anyInt())).thenReturn(new PageImpl<>(List.of(
                new ManagerOrderResponse("UTE123", "Người gửi", "Người nhận", "Chưa phân công",
                        Order.OrderStatus.PENDING_PICKUP, new BigDecimal("30000"), LocalDateTime.of(2026, 9, 18, 9, 0)))));
        attempts.clearFailures("admin01"); attempts.clearFailures("manager01");
        when(dispatch.findPendingOrders(anyString(), anyInt())).thenReturn(new PageImpl<>(List.of(
            new DispatchOrderResponse(1L, "UTEDEMO", "Người nhận", "Chưa phân công", 0))));
        when(dispatch.findShippers(anyString(), anyInt())).thenReturn(new PageImpl<>(List.of(
            new ShipperWorkloadResponse(3L, "shipper01", "Shipper", 2))));
        when(sharedDispatch.pending(anyString(), any(), anyInt())).thenReturn(Page.empty());
        when(sharedDispatch.shippers(anyString(), anyInt())).thenReturn(Page.empty());
        when(promotions.findPromotions(anyString(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(audit.findEvents(anyString(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        var tariff = new vn.edu.hcmute.uteexpress.entity.admin.ShippingTariff();
        tariff.setServiceType(Order.ServiceType.STANDARD); tariff.setBaseFee(BigDecimal.valueOf(15000));
        tariff.setFeePerKg(BigDecimal.valueOf(3000));
        when(tariffs.findTariffs(anyString())).thenReturn(List.of(ShippingTariffResponse.from(tariff)));
        var counts = new EnumMap<Order.OrderStatus, Long>(Order.OrderStatus.class);
        for (var status : Order.OrderStatus.values()) { counts.put(status, 0L); }
        when(reports.report(anyString(), any(), any(), any())).thenReturn(
            new OperationsReportResponse(LocalDate.now().minusDays(29), LocalDate.now(), counts, 0, BigDecimal.ZERO.setScale(1), BigDecimal.ZERO));
    }

    @Test void newManagerPagesRenderAndAdminPagesAreDenied() throws Exception {
        for (var path : List.of("/manager/phan-cong", "/manager/khuyen-mai", "/manager/khuyen-mai/tao", "/manager/bang-cuoc", "/manager/bao-cao")) {
            mvc.perform(get(path).with(authentication(auth(manager)))).andExpect(status().isOk());
        }
        for (var path : List.of("/admin/bang-cuoc", "/admin/bang-cuoc/STANDARD", "/admin/nhat-ky")) {
            mvc.perform(get(path).with(authentication(auth(manager)))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(authentication(auth(admin)))).andExpect(status().isOk());
        }
        verify(tariffs, never()).updateTariff(anyString(), any(), any());
    }

    @Test void adminAccountCrudPagesRenderButManagerCannotReadOrWrite() throws Exception {
        for (var suffix : List.of("", "/tao", "/3/sua", "/3/xoa", "/3/mat-khau")) {
            mvc.perform(get("/admin/quan-ly-tai-khoan" + suffix).with(authentication(auth(admin))))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("not-rendered-hash"))));
            mvc.perform(get("/admin/quan-ly-tai-khoan" + suffix).with(authentication(auth(manager))))
                .andExpect(status().isForbidden());
        }
        for (var suffix : List.of("/tao", "/3/sua", "/3/xoa", "/3/mat-khau")) {
            mvc.perform(post("/admin/quan-ly-tai-khoan" + suffix).with(authentication(auth(manager))).with(csrf()))
                .andExpect(status().isForbidden());
            mvc.perform(post("/admin/quan-ly-tai-khoan" + suffix).with(authentication(auth(admin))))
                .andExpect(status().isForbidden());
        }
        verify(allAccounts, never()).createAccount(anyString(), any());
        verify(allAccounts, never()).updateAccount(anyString(), anyLong(), any());
        verify(allAccounts, never()).deleteAccount(anyString(), anyLong(), any());
        verify(allAccounts, never()).resetPassword(anyString(), anyLong(), any());
    }

    @Test void invalidAccountFormClearsPasswordAndDoesNotWrite() throws Exception {
        mvc.perform(post("/admin/quan-ly-tai-khoan/tao").with(authentication(auth(admin))).with(csrf())
            .param("username", "bad").param("fullName", "<script>alert(1)</script>")
            .param("email", "not-mail").param("phone", "0123456789").param("role", "ROOT")
            .param("credentials.password", "DoNotEcho@2026").param("credentials.confirmPassword", "DoNotEcho@2026"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "username", "fullName", "email", "phone", "role"))
            .andExpect(content().string(not(containsString("DoNotEcho@2026"))))
            .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        verify(allAccounts, never()).createAccount(anyString(), any());
    }

    @Test void editAndResetRejectInvalidNestedFieldsWithoutEchoingSecrets() throws Exception {
        mvc.perform(post("/admin/quan-ly-tai-khoan/3/sua").with(authentication(auth(admin))).with(csrf())
            .param("revision", "-1").param("profile.fullName", "1234").param("profile.email", "bad")
            .param("profile.phone", "123").param("profile.role", "ROOT").param("active", "not-boolean")
            .param("reason", "x").param("currentPassword", "PrivateSecret@2026"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "revision", "profile.fullName", "profile.email", "profile.phone", "profile.role", "active", "reason"))
            .andExpect(content().string(not(containsString("PrivateSecret@2026"))));
        mvc.perform(post("/admin/quan-ly-tai-khoan/3/mat-khau").with(authentication(auth(admin))).with(csrf())
            .param("revision", "0").param("credentials.password", "weak").param("credentials.confirmPassword", "different")
            .param("currentPassword", "PrivateSecret@2026").param("reason", "Kiểm thử mật khẩu"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "credentials.password"))
            .andExpect(content().string(not(containsString("PrivateSecret@2026"))));
        verify(allAccounts, never()).updateAccount(anyString(), anyLong(), any());
        verify(allAccounts, never()).resetPassword(anyString(), anyLong(), any());
    }

    @Test void validEditAndResetUseAdminPrincipalNotInjectedActor() throws Exception {
        mvc.perform(post("/admin/quan-ly-tai-khoan/3/sua").with(authentication(auth(admin))).with(csrf())
            .param("revision", "0").param("profile.fullName", "Người dùng mới").param("profile.email", "user@example.com")
            .param("profile.phone", "0901234567").param("profile.role", "USER").param("active", "true")
            .param("reason", "Cập nhật kiểm thử").param("currentPassword", "PrivateSecret@2026").param("actor", "manager01"))
            .andExpect(redirectedUrl("/admin/quan-ly-tai-khoan"));
        mvc.perform(post("/admin/quan-ly-tai-khoan/3/mat-khau").with(authentication(auth(admin))).with(csrf())
            .param("revision", "0").param("credentials.password", "NewPassword@2026").param("credentials.confirmPassword", "NewPassword@2026")
            .param("currentPassword", "PrivateSecret@2026").param("reason", "Cấp lại kiểm thử").param("actor", "manager01"))
            .andExpect(redirectedUrl("/admin/quan-ly-tai-khoan"));
        verify(allAccounts).updateAccount(eq("admin01"), eq(3L), org.mockito.ArgumentMatchers.any(AdminAccountUpdateRequest.class));
        verify(allAccounts).resetPassword(eq("admin01"), eq(3L), org.mockito.ArgumentMatchers.any(AdminAccountPasswordRequest.class));
    }

    @Test void deletionNeedsValidFormUsesAuthenticatedActorAndNeverRunsOnGet() throws Exception {
        mvc.perform(get("/admin/quan-ly-tai-khoan/3/xoa").with(authentication(auth(admin)))).andExpect(status().isOk());
        verify(allAccounts, never()).deleteAccount(anyString(), anyLong(), any());
        mvc.perform(post("/admin/quan-ly-tai-khoan/3/xoa").with(authentication(auth(admin))).with(csrf())
            .param("revision", "bad").param("reason", "").param("currentPassword", "Secret@2026"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "revision", "reason", "confirmedUsername"))
            .andExpect(content().string(not(containsString("Secret@2026"))));
        verify(allAccounts, never()).deleteAccount(anyString(), anyLong(), any());
        mvc.perform(post("/admin/quan-ly-tai-khoan/3/xoa").with(authentication(auth(admin))).with(csrf())
            .param("revision", "0").param("reason", "Xóa tài khoản kiểm thử").param("currentPassword", "Secret@2026")
            .param("confirmedUsername", "userdemo").param("actor", "manager01"))
            .andExpect(redirectedUrl("/admin/quan-ly-tai-khoan"));
        verify(allAccounts).deleteAccount(eq("admin01"), eq(3L), org.mockito.ArgumentMatchers.any(AdminAccountDeleteRequest.class));
    }

    @Test void newWritesRequireCsrfAndRejectBadBinding() throws Exception {
        mvc.perform(post("/manager/phan-cong/1").with(authentication(auth(manager))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/admin/bang-cuoc/STANDARD").with(authentication(auth(manager))).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/admin/bang-cuoc/STANDARD").with(authentication(auth(admin))).with(csrf())
            .param("baseFee", "not-a-number").param("feePerKg", "-1").param("revision", "0").param("reason", ""))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "baseFee", "feePerKg", "reason"));
        mvc.perform(post("/manager/khuyen-mai/tao").with(authentication(auth(manager))).with(csrf())
            .param("discountType", "ADMIN").param("startAt", "wrong-date"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "discountType", "startAt"));
        verify(promotions, never()).savePromotion(anyString(), any(), any());
        verify(tariffs, never()).updateTariff(anyString(), any(), any());
    }

    @Test void assignmentUsesAuthenticatedActorAndServerPath() throws Exception {
        mvc.perform(post("/manager/phan-cong").with(authentication(auth(manager))).with(csrf())
            .param("shipperId", "3").param("expectedShipperId", "0").param("trackingCode", "UTEDEMO")
            .param("expectedUpdatedAt", "2026-10-15T09:00:00").param("note", "Điều phối theo tải đơn")
            .param("actor", "admin01").param("status", "DELIVERED"))
            .andExpect(redirectedUrl("/manager/phan-cong"));
        verify(sharedDispatch).assign(eq("UTEDEMO"), eq(3L), eq(0L), org.mockito.ArgumentMatchers.any(LocalDateTime.class), eq("Điều phối theo tải đơn"), eq("manager01"));
    }

    @Test void csvContainsOnlyAggregatesAndInvalidDatesAre400() throws Exception {
        mvc.perform(get("/manager/bao-cao/csv").with(authentication(auth(manager)))
            .param("from", "2026-10-01").param("to", "2026-10-10"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(containsString("Cuoc_da_thanh_toan_VND,0")))
            .andExpect(content().string(containsString("Chi_nhanh,")));
        mvc.perform(get("/manager/bao-cao").with(authentication(auth(manager))).param("from", "not-a-date"))
            .andExpect(status().isBadRequest());
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

    @Test void bothDashboardsRenderOnlyTwoNecessaryChartsUsingAuthenticatedActor() throws Exception {
        for (var path : List.of("/admin/trang-chu", "/manager/trang-chu")) {
            var html = mvc.perform(get(path).param("actor","someone-else").with(authentication(auth(admin))))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Trạng thái đơn hàng")))
                    .andExpect(content().string(containsString("Đơn mới trong 14 ngày")))
                    .andExpect(content().string(containsString("không phải tỷ lệ giao thành công")))
                    .andReturn().getResponse().getContentAsString();
            // Chỉ kiểm tra dữ liệu biểu đồ; token CSRF ngẫu nhiên ở phần khác có thể chứa các chuỗi này.
            int chartStart = html.indexOf("class=\"staff-dashboard-charts\"");
            int chartEnd = html.indexOf("class=\"staff-charts-snapshot\"", chartStart);
            org.junit.jupiter.api.Assertions.assertTrue(chartStart >= 0 && chartEnd > chartStart);
            String chartHtml = html.substring(chartStart, chartEnd);
            org.junit.jupiter.api.Assertions.assertFalse(chartHtml.contains("NaN"));
            org.junit.jupiter.api.Assertions.assertFalse(chartHtml.contains("Infinity"));
            org.junit.jupiter.api.Assertions.assertEquals(2, html.split("staff-chart-card",-1).length - 1);
        }
        verify(dashboardCharts).getAdminCharts("admin01"); verify(dashboardCharts).getOperationsCharts("admin01");
    }

    @Test void chartsHaveExplicitEmptyStatesInsteadOfBlankOrBrokenCharts() throws Exception {
        var today = LocalDate.now();
        var empty = new DashboardChartsResponse(today.minusDays(13),today,LocalDateTime.now(),List.of(),List.of(),0,0,0);
        when(dashboardCharts.getOperationsCharts("manager01")).thenReturn(empty);
        mvc.perform(get("/manager/trang-chu").with(authentication(auth(manager))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Chưa có đơn hàng để thống kê trạng thái.")))
                .andExpect(content().string(containsString("Không có đơn mới trong 14 ngày gần đây.")));
    }

    @Test void chartDatabaseFailureReturns503WithoutLeakingConnectionDetails() throws Exception {
        when(dashboardCharts.getOperationsCharts("manager01")).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("private-database-address"));
        mvc.perform(get("/manager/trang-chu").with(authentication(auth(manager))))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString("private-database-address"))));
    }

    private static DashboardChartsResponse chartFixture() {
        var today = LocalDate.now();
        long[] created = {1,0,0,0,2,0,0,0,0,3,0,0,0,2};
        return new DashboardChartsResponse(today.minusDays(13),today,LocalDateTime.now(),List.of(
                new DashboardChartsResponse.CategoryResponse("PENDING_PICKUP","Chờ lấy hàng","amber",3,new BigDecimal("37.50")),
                new DashboardChartsResponse.CategoryResponse("PICKED_UP","Đã lấy hàng","cyan",1,new BigDecimal("12.50")),
                new DashboardChartsResponse.CategoryResponse("IN_TRANSIT","Đang vận chuyển","blue",1,new BigDecimal("12.50")),
                new DashboardChartsResponse.CategoryResponse("DELIVERED","Giao thành công","green",2,new BigDecimal("25.00")),
                new DashboardChartsResponse.CategoryResponse("FAILED","Giao thất bại","rose",1,new BigDecimal("12.50")),
                new DashboardChartsResponse.CategoryResponse("CANCELLED","Đã hủy","slate",0,BigDecimal.ZERO),
                new DashboardChartsResponse.CategoryResponse("RETURNED","Đã hoàn hàng","violet",0,BigDecimal.ZERO)),
                java.util.stream.IntStream.range(0,14).mapToObj(i -> new DashboardChartsResponse.DayResponse(
                        today.minusDays(13-i),created[i],BigDecimal.valueOf(created[i]).multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(3),2,java.math.RoundingMode.HALF_UP))).toList(),8,8,3);
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
                .param("password", "OriginalPass@2026")).andExpect(redirectedUrl("/noi-bo/dang-nhap?error&locked"));
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
        when(dashboard.getSummary(anyString())).thenThrow(new IllegalStateException("jdbc:SECRET_DATABASE_PASSWORD"));
        mvc.perform(get("/manager/trang-chu").with(authentication(auth(manager))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("SECRET_DATABASE_PASSWORD"))));
    }

    @Test void privilegedOtpActivationIsBlockedWithoutCallingSharedService() throws Exception {
        when(users.findByUsername("admin01")).thenReturn(Optional.of(admin.getUser()));
        mvc.perform(post("/xac-thuc-otp").with(csrf()).param("username", "admin01").param("otp", "123456"))
                .andExpect(status().isOk()).andExpect(view().name("auth/verify-otp"))
                .andExpect(model().attributeExists("error"));
        verifyNoInteractions(authService);
    }

    @Test void privilegedPublicPasswordRecoveryIsBlocked() throws Exception {
        when(users.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(manager.getUser()));
        mvc.perform(post("/quen-mat-khau").with(csrf()).param("email", "manager@example.com"))
                .andExpect(status().isOk()).andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("otpJustSent", true));
        mvc.perform(post("/dat-lai-mat-khau").with(csrf()).param("email", "manager@example.com"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        verifyNoInteractions(authService);
    }

    @Test void forgedUsernameCannotBypassPrivilegedEmailPolicy() throws Exception {
        when(users.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(manager.getUser()));
        mvc.perform(post("/quen-mat-khau").with(csrf()).param("email", "manager@example.com").param("username", "customer"))
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
        when(orders.findOrders(anyString(), any(), anyString(), any(), any(), any(), anyInt())).thenReturn(Page.empty());
        mvc.perform(get("/manager/don-hang").with(authentication(auth(manager))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Không có đơn phù hợp")));
    }

    @Test void customerOtpFlowRemainsUnchanged() throws Exception {
        var customer = new AppUser("customer", "hash", "customer@example.com");
        when(users.findByUsername("customer")).thenReturn(Optional.of(customer));
        when(authService.verifyOtp("customer", "123456")).thenReturn(true);
        mvc.perform(post("/xac-thuc-otp").with(csrf()).param("username", "customer").param("otp", "123456"))
                .andExpect(status().isOk()).andExpect(view().name("auth/login"));
        verify(authService).verifyOtp("customer", "123456");
    }

    @Test void newWorkspacePagesRespectRolesAndRenderScopedSidebar() throws Exception {
        for (var account : List.of(admin, manager)) {
            for (String path : List.of("/manager/tra-cuu-hang-loat", "/noi-bo/tai-khoan")) {
                mvc.perform(get(path).with(authentication(auth(account)))).andExpect(status().isOk())
                    .andExpect(content().string(containsString("staff-workspace")))
                    .andExpect(content().string(containsString("Tìm chức năng")))
                    .andExpect(content().string(not(containsString("not-exposed-hash"))));
            }
        }
        mvc.perform(get("/admin/an-toan-truy-cap").with(authentication(auth(admin))))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Đăng xuất trên mọi thiết bị")));
        mvc.perform(get("/noi-bo/truy-cap-api").with(authentication(auth(admin)))).andExpect(status().isOk());
        mvc.perform(get("/noi-bo/truy-cap-api").with(authentication(auth(manager)))).andExpect(status().isForbidden());
        mvc.perform(post("/noi-bo/truy-cap-api").with(authentication(auth(manager))).with(csrf()).param("password", "test"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin/an-toan-truy-cap").with(authentication(auth(manager)))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/an-toan-truy-cap/2/thu-hoi").with(authentication(auth(manager))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/manager/tra-cuu-hang-loat").with(user("user01").roles("USER"))).andExpect(status().isForbidden());
    }
    @Test void newPostsRequireCsrfAndDoNotTrustSubmittedActor() throws Exception {
        mvc.perform(post("/noi-bo/truy-cap-api").with(authentication(auth(admin))).param("password", "test"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/manager/tra-cuu-hang-loat").with(authentication(auth(manager))).param("codes", "UTE1"))
            .andExpect(status().isForbidden());
        when(workbench.findShipments(anyString(), any())).thenReturn(List.of());
        mvc.perform(post("/manager/tra-cuu-hang-loat").with(authentication(auth(manager))).with(csrf())
            .param("actor", "admin01").param("codes", "UTE1")).andExpect(status().isOk());
        verify(workbench).findShipments(eq("manager01"), any());
        when(apiAccess.issueToken(anyString(), any())).thenReturn("test-token-not-a-real-secret");
        mvc.perform(post("/noi-bo/truy-cap-api").with(authentication(auth(admin))).with(csrf())
            .param("actor", "manager01").param("password", "password-never-rendered"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(not(containsString("password-never-rendered"))));
        verify(apiAccess).issueToken(eq("admin01"), any());
    }
    @Test void invalidNewFormsDoNotReachServicesAndCanRenderSafeErrors() throws Exception {
        mvc.perform(post("/noi-bo/truy-cap-api").with(authentication(auth(admin))).with(csrf()).param("password", ""))
            .andExpect(status().isOk());
        mvc.perform(post("/manager/tra-cuu-hang-loat").with(authentication(auth(manager))).with(csrf()).param("codes", ""))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "codes"));
        mvc.perform(post("/admin/an-toan-truy-cap/2/thu-hoi").with(authentication(auth(admin))).with(csrf())
            .param("revision", "-1").param("password", "").param("reason", " "))
            .andExpect(status().is3xxRedirection());
        verify(apiAccess, never()).issueToken(anyString(), any()); verify(apiAccess, never()).revokeAccess(anyString(), any(), any());
        verify(workbench, never()).findShipments(anyString(), any());
    }
    @Test void renderOptionalStaffPreviewUsingOnlySyntheticAccounts() throws Exception {
        if (!Boolean.getBoolean("staff.preview")) return;
        var root = java.nio.file.Path.of("target/staff-preview"); java.nio.file.Files.createDirectories(root);
        try (var assets = java.nio.file.Files.walk(java.nio.file.Path.of("target/classes/static"))) {
            for (var source : assets.toList()) {
                var destination = root.resolve(java.nio.file.Path.of("target/classes/static").relativize(source));
                if (java.nio.file.Files.isDirectory(source)) java.nio.file.Files.createDirectories(destination);
                else java.nio.file.Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        for (var entry : Map.of("manager.html", "/manager/trang-chu", "admin.html", "/admin/trang-chu", "batch.html", "/manager/tra-cuu-hang-loat",
                "access.html", "/admin/an-toan-truy-cap", "api.html", "/noi-bo/truy-cap-api").entrySet()) {
            var account = entry.getKey().equals("manager.html") ? manager : admin;
            String html = mvc.perform(get(entry.getValue()).with(authentication(auth(account)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(root.resolve(entry.getKey()), html, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test void normalMenuHidesTokenToolButKeepsUsefulRoleSpecificFunctions() throws Exception {
        for (var account : List.of(admin,manager)) {
            String html = mvc.perform(get("/noi-bo/tai-khoan").with(authentication(auth(account))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            var nav = java.util.regex.Pattern.compile("<nav[^>]*class=\"staff-nav\"[^>]*>.*?</nav>",java.util.regex.Pattern.DOTALL).matcher(html);
            Assertions.assertTrue(nav.find());
            Assertions.assertFalse(nav.group().contains("/noi-bo/truy-cap-api"));
            Assertions.assertTrue(nav.group().contains("/noi-bo/tai-khoan")); Assertions.assertTrue(nav.group().contains("/noi-bo/doi-mat-khau"));
            if (account == manager) {
                Assertions.assertFalse(nav.group().contains("/admin/an-toan-truy-cap")); Assertions.assertFalse(nav.group().contains("/admin/nhat-ky"));
            } else {
                Assertions.assertTrue(nav.group().contains("/admin/an-toan-truy-cap")); Assertions.assertTrue(nav.group().contains("/admin/nhat-ky"));
            }
        }
        mvc.perform(get("/admin/an-toan-truy-cap").with(authentication(auth(admin))))
            .andExpect(content().string(containsString("Tích hợp kỹ thuật")))
            .andExpect(content().string(containsString("name=\"confirmSelf\"")))
            .andExpect(content().string(not(containsString("<th>Phiên bản truy cập</th>"))));
    }
    @Test void ownProfileIgnoresSubmittedIdentityAndDoesNotAllowUpdates() throws Exception {
        mvc.perform(get("/noi-bo/tai-khoan").param("username","admin01").with(authentication(auth(manager))))
            .andExpect(status().isOk()).andExpect(content().string(containsString("manager01@example.com")))
            .andExpect(content().string(not(containsString("admin01@example.com"))))
            .andExpect(content().string(not(containsString(manager.getUser().getPassword()))));
        verify(staffService).findOwnProfile("manager01"); verify(staffService,never()).findOwnProfile("admin01");
        mvc.perform(post("/noi-bo/tai-khoan").with(authentication(auth(manager))).with(csrf()).param("role","ADMIN"))
            .andExpect(status().isMethodNotAllowed());
        verify(staffService,never()).updateAccount(anyString(),any(),any());
        mvc.perform(get("/noi-bo/tai-khoan").with(user("customer").roles("USER"))).andExpect(status().isForbidden());
    }
    @Test void auditFiltersRenderFriendlyLabelsAndEscapeStoredText() throws Exception {
        when(audit.findEvents(eq("admin01"),org.mockito.ArgumentMatchers.any(AuditFilterRequest.class),eq(0))).thenReturn(new PageImpl<>(List.of(
            new AuditEventResponse("admin01","ACCESS_REVOKE","staff:2","<script>private-text</script>",LocalDateTime.now()))));
        mvc.perform(get("/admin/nhat-ky").with(authentication(auth(admin))).param("category","SECURITY").param("operator","admin01"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Thu hồi truy cập")))
            .andExpect(content().string(containsString("&lt;script&gt;private-text&lt;/script&gt;")))
            .andExpect(content().string(not(containsString("<script>private-text</script>"))));
        verify(audit).findEvents(eq("admin01"),org.mockito.ArgumentMatchers.argThat(filter -> filter.getCategory() == AuditFilterRequest.Category.SECURITY && filter.getOperator().equals("admin01")),eq(0));
    }
    @Test void malformedAuditFiltersReturn400WithoutRunningService() throws Exception {
        for (var params : List.of(Map.of("category","INVALID"),Map.of("from","2026-02-30"),Map.of("operator","bad<script>"),Map.of("operator","a".repeat(51)))) {
            var request = get("/admin/nhat-ky").with(authentication(auth(admin)));
            params.forEach(request::param); mvc.perform(request).andExpect(status().isBadRequest());
        }
        verify(audit,never()).findEvents(anyString(),anyInt());
        verify(audit,never()).findEvents(anyString(),org.mockito.ArgumentMatchers.any(AuditFilterRequest.class),anyInt());
    }
    @Test void auditDateValidationAndDatabaseErrorsAreNotSilentlyHidden() throws Exception {
        when(audit.findEvents(eq("admin01"),org.mockito.ArgumentMatchers.any(AuditFilterRequest.class),eq(0)))
            .thenThrow(new IllegalArgumentException("Khoảng ngày không hợp lệ."));
        mvc.perform(get("/admin/nhat-ky").with(authentication(auth(admin))).param("from","2026-10-18").param("to","2026-10-01"))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Khoảng ngày không hợp lệ.")));
        when(audit.findEvents(eq("admin01"),org.mockito.ArgumentMatchers.any(AuditFilterRequest.class),eq(0)))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private-database-detail"));
        mvc.perform(get("/admin/nhat-ky").with(authentication(auth(admin))).param("category","ACCOUNT"))
            .andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString("private-database-detail"))));
    }

    @Test void branchPagesRequireAdminAndRenderAvailableHubAreas() throws Exception {
        for (String path : List.of("/admin/chi-nhanh", "/admin/chi-nhanh/1/sua")) {
            mvc.perform(get(path).with(authentication(auth(admin)))).andExpect(status().isOk())
                    .andExpect(content().string(containsString("SGN")));
            mvc.perform(get(path).with(authentication(auth(manager)))).andExpect(status().isForbidden());
        }
    }
    @Test void branchUpdateRequiresAdminCsrfAndValidForm() throws Exception {
        mvc.perform(post("/admin/chi-nhanh/1/sua").with(authentication(auth(manager))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/chi-nhanh/1/sua").with(authentication(auth(admin)))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/chi-nhanh/1/sua").with(authentication(auth(admin))).with(csrf())
                .param("name", "").param("revision", "-1"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "name", "revision"));
        verify(branchAdmin, never()).update(anyString(), anyLong(), any());
        mvc.perform(post("/admin/chi-nhanh/1/sua").with(authentication(auth(admin))).with(csrf())
                .param("name", "Kho SGN mới").param("revision", "0").param("hubAddress", "Địa chỉ kho mới")
                .param("hubAreaCode", "SGN-THUDUC").param("active", "true").param("reason", "Kiểm thử sửa kho"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/chi-nhanh"));
        verify(branchAdmin).update(eq("admin01"), eq(1L), any());
    }

    @Test void deniedPostLandsOnTheAccessDeniedPageInsteadOfMethodNotAllowed() throws Exception {
        // accessDeniedHandler forward nguyên phương thức POST tới /noi-bo/tu-choi; trang phải nhận POST, không trả 405.
        mvc.perform(post("/noi-bo/tu-choi").with(authentication(auth(manager))).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(content().string(containsString("phiên làm việc đã hết hạn")));
        mvc.perform(get("/noi-bo/tu-choi")).andExpect(status().isForbidden());
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
