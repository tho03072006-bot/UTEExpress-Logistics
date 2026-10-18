package vn.edu.hcmute.uteexpress.tracking;

import jakarta.servlet.ServletContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.JwtTokenProvider;
import vn.edu.hcmute.uteexpress.service.admin.StaffApiAccessService;
import vn.edu.hcmute.uteexpress.service.manager.StaffWorkbenchService;
import static org.junit.jupiter.api.Assertions.*;

/** Tomcat thật + H2 in-memory riêng; không khởi tạo/kết nối database local hay ghi Aiven. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:staff-workspace-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.jwt.api.enabled=true", "app.jwt.api.ttl-minutes=30",
    "app.jwt.api.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@ActiveProfiles("isolated-logistics") @Transactional @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StaffWorkspaceIntegrationTest {
    @Autowired ServletContext servletContext;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired OrderRepository orders;
    @Autowired PasswordEncoder encoder;
    @Autowired StaffWorkbenchService workbench;
    @Autowired StaffApiAccessService api;
    @Autowired JwtTokenProvider tokens;
    @Autowired vn.edu.hcmute.uteexpress.service.admin.AuditService auditService;
    @Autowired vn.edu.hcmute.uteexpress.service.admin.StaffAccountService staffService;
    @Autowired vn.edu.hcmute.uteexpress.repository.admin.AuditEventRepository auditEvents;
    private StaffAccount manager;
    private Order shipment;
    @BeforeEach void setUp() {
        var admin = user("staffadmin", AppUser.Role.ADMIN); internal(admin);
        manager = internal(user("staffmanager", AppUser.Role.MANAGER));
        var customer = user("staffcustomer", AppUser.Role.USER);
        shipment = new Order(); shipment.setTrackingCode("UTEBATCH01"); shipment.setSender(customer);
        shipment.setShippingFee(new BigDecimal("18000")); shipment.setReceiverName("Không xuất thông tin riêng");
        shipment = orders.saveAndFlush(shipment);
    }
    @Test void actualTomcatUsesSixtyMinuteSessionTimeout() { assertEquals(60, servletContext.getSessionTimeout()); }
    @Test void batchQueryWorksAgainstRealMappingsAndIncludesUnknownCodes() {
        var form = form("utebatch01,NO-SUCH-CODE,UTEBATCH01");
        var results = workbench.findShipments("staffmanager",form);
        assertEquals(List.of("UTEBATCH01","NO-SUCH-CODE"),results.stream().map(BatchTrackingResponse::trackingCode).toList());
        assertTrue(results.get(0).found()); assertFalse(results.get(1).found());
        String csv = workbench.exportShipments("staffmanager",form); assertFalse(csv.contains("Không xuất thông tin riêng"));
    }
    @Test void workQueuesUseRealSharedOrderAndPaymentState() {
        var queues = workbench.getWorkQueues("staffmanager");
        assertEquals(1,queues.get(0).count()); assertEquals(1,queues.get(2).count());
        assertEquals(Order.OrderStatus.PENDING_PICKUP,orders.findById(shipment.getId()).orElseThrow().getStatus());
    }
    @Test void issuingAndRevokingTokenUsesDatabaseVersionAndPasswordConfirmation() {
        assertThrows(AccessDeniedException.class, () -> api.issueToken("staffmanager",new ApiAccessRequest("CurrentPassword@2026")));
        String token = tokens.generateToken(manager);
        String adminToken = api.issueToken("staffadmin",new ApiAccessRequest("CurrentPassword@2026"));
        assertEquals("staffadmin",tokens.parseToken(adminToken).getSubject());
        assertTrue(tokens.matchesAccount(tokens.parseToken(token),manager));
        long revision = manager.getRevision();
        api.revokeAccess("staffadmin",manager.getId(),new AccessRevocationRequest(revision,"CurrentPassword@2026","Kiểm thử thu hồi token",false));
        assertFalse(tokens.matchesAccount(tokens.parseToken(token),staff.findById(manager.getId()).orElseThrow()));
        assertThrows(StaffConflictException.class, () -> api.revokeAccess("staffadmin",manager.getId(),
            new AccessRevocationRequest(revision,"CurrentPassword@2026","Không dùng phiên bản cũ",false)));
    }
    @Test void managerCannotRevokeOrViewAdminAccessAndCustomerCannotUseWorkbench() {
        assertThrows(AccessDeniedException.class, () -> api.findAccess("staffmanager",0));
        assertThrows(AccessDeniedException.class, () -> api.revokeAccess("staffmanager",manager.getId(),
            new AccessRevocationRequest(manager.getRevision(),"CurrentPassword@2026","Không được quản trị tài khoản",false)));
        assertThrows(AccessDeniedException.class, () -> workbench.findShipments("staffcustomer",form("UTEBATCH01")));
    }
    @Test void auditFilterMatchesActorCategoryDateAndPaginationWithRealQueries() {
        var today = java.time.LocalDate.now();
        for (int i = 0; i < 21; i++) saveEvent("staffadmin","ACCOUNT_UPDATE",today.atTime(10,0).plusMinutes(i));
        saveEvent("staffmanager","PROMOTION_UPDATE",today.atTime(11,0));
        saveEvent("staffadmin","ACCESS_REVOKE",today.minusDays(1).atTime(10,0));
        var filter = new AuditFilterRequest(); filter.setOperator("staffadmin"); filter.setCategory(AuditFilterRequest.Category.ACCOUNT);
        filter.setFrom(today); filter.setTo(today);
        var first = auditService.findEvents("staffadmin",filter,0); assertEquals(21,first.getTotalElements()); assertEquals(20,first.getNumberOfElements());
        assertEquals(1,auditService.findEvents("staffadmin",filter,1).getNumberOfElements());
        filter.setCategory(AuditFilterRequest.Category.SECURITY); assertTrue(auditService.findEvents("staffadmin",filter,0).isEmpty());
        filter.setFrom(today.minusDays(1)); assertEquals(1,auditService.findEvents("staffadmin",filter,0).getTotalElements());
        assertThrows(AccessDeniedException.class,() -> auditService.findEvents("staffmanager",filter,0));
    }
    @Test void ownProfileUsesIssuedAccountAndNeverReturnsAuthenticationSecrets() {
        var profile = staffService.findOwnProfile("staffmanager");
        assertEquals("staffmanager",profile.username()); assertEquals(AppUser.Role.MANAGER,profile.role());
        assertFalse(profile.toString().contains(manager.getUser().getPassword()));
        assertThrows(AccessDeniedException.class,() -> staffService.findOwnProfile("staffcustomer"));
    }
    private void saveEvent(String actor,String action,java.time.LocalDateTime time) {
        var event = new vn.edu.hcmute.uteexpress.entity.admin.AuditEvent(); event.setActor(actor); event.setAction(action);
        event.setTarget("test-only"); event.setDetail("Nội dung kiểm thử"); event.setCreatedAt(time); auditEvents.saveAndFlush(event);
    }

    private AppUser user(String username, AppUser.Role role) {
        var user = new AppUser(username,encoder.encode("CurrentPassword@2026"),username + "@test.invalid");
        user.setEnabled(true); user.setRole(role); return users.saveAndFlush(user);
    }
    private StaffAccount internal(AppUser user) {
        var account = new StaffAccount(); account.setUser(user); account.setIssuedBy("test-only"); account.setPasswordChangeRequired(false);
        return staff.saveAndFlush(account);
    }
    private static BatchTrackingRequest form(String codes) { var form = new BatchTrackingRequest(); form.setCodes(codes); return form; }
}
