package vn.edu.hcmute.uteexpress.validation;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.StaffBranch;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.security.admin.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.tracking.OrderComplaintService;
import vn.edu.hcmute.uteexpress.support.RoutingFixtures;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Render template thật với principal thật trên H2; không thay cho nghiệm thu bố cục bằng trình duyệt. */
@SpringBootTest(classes=vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class,properties={
    "spring.datasource.url=jdbc:h2:mem:all-role-render;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.mail.order-updates.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("isolated-logistics") @Transactional
class AllRolePageRenderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired BranchRepository branches;
    @Autowired OrderRepository orders;
    @Autowired OrderComplaintRepository complaintRecords;
    @Autowired OrderPaymentRepository payments;
    @Autowired OrderService orderService;
    @Autowired OrderComplaintService complaints;
    final Map<String,Authentication> actors=new HashMap<>();
    Order order;
    Long complaintId;
    @BeforeEach void seed() {
        RoutingFixtures.seedH2(context);
        for(var role:AppUser.Role.values()) {
            String username="render-"+role.name().toLowerCase(Locale.ROOT);
            var user=new AppUser(username,"test-only-hash",username+"@test.invalid");user.setEnabled(true);user.setRole(role);
            user.setFullName("Kiểm thử Nguyễn Thị Ánh");user.setPhone("0901234567");user=users.saveAndFlush(user);
            org.springframework.security.core.userdetails.UserDetails principal;
            if(role==AppUser.Role.MANAGER || role==AppUser.Role.ADMIN) {
                var internal=new StaffAccount();internal.setUser(user);internal.setActive(true);internal.setPasswordChangeRequired(false);internal.setIssuedBy("test-only");
                principal=new StaffPrincipal(staff.saveAndFlush(internal));
            } else principal=new PublicAccountPrincipal(user);
            if(role==AppUser.Role.MANAGER || role==AppUser.Role.SHIPPER)
                staffBranches.saveAndFlush(new StaffBranch(user,branches.findByCode("SGN").orElseThrow(),user,LocalDateTime.now()));
            actors.put(role.name(),new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        }
        var form=new OrderCreateRequest();RoutingFixtures.fill(form);
        form.setSenderName("Kiểm thử Nguyễn Thị Ánh");form.setSenderAddress("Số 1 Võ Văn Ngân");form.setReceiverName("Kiểm thử người nhận");
        form.setReceiverAddress("Số 2 đường kiểm thử");form.setReceiverPhone("0901234567");form.setWeightKg(1d);
        form.setServiceType(Order.ServiceType.STANDARD);form.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        order=orderService.createOrder(form,"render-user");order.setShipper(users.findByUsername("render-shipper").orElseThrow());orders.saveAndFlush(order);
        complaints.create(order.getId(),"render-user","Kiểm thử tiếng Việt và hội thoại","Hàng dễ vỡ, cần kiểm tra đủ dấu: ă â đ ê ô ơ ư",null);
        complaintId=complaintRecords.findByOrder(order).orElseThrow().getId();
        complaints.customerMessage(complaintId,"render-user","Kiểm thử <script>alert('x')</script> & dấu tiếng Việt");
    }
    static Stream<Arguments> pages() {
        var rows=new ArrayList<Arguments>();
        add(rows,"GUEST","/","/tra-cuu","/uoc-tinh-cuoc","/dang-nhap","/dang-ky","/quen-mat-khau","/noi-bo/dang-nhap");
        add(rows,"USER","/nguoi-dung/trang-chu","/nguoi-dung/tao-don","/nguoi-dung/gio-don","/nguoi-dung/dia-chi","/nguoi-dung/dia-chi/them",
                "/nguoi-dung/don-hang","/nguoi-dung/tai-khoan","/nguoi-dung/tai-khoan/doi-mat-khau","/nguoi-dung/don-hang/{order}",
                "/nguoi-dung/don-hang/{order}/hanh-trinh","/nguoi-dung/don-hang/{order}/xu-ly","/nguoi-dung/khieu-nai/{complaint}");
        add(rows,"SHIPPER","/shipper/trang-chu","/shipper/lo-trinh","/shipper/thong-bao","/shipper/doi-soat","/shipper/thong-ke","/shipper/tai-khoan",
                "/shipper/tai-khoan/doi-mat-khau","/shipper/quet-ma","/shipper/don/{order}","/shipper/don/{order}/hanh-trinh","/shipper/don/{order}/xu-ly","/shipper/khieu-nai/{complaint}");
        String[] managerPages={"/manager/trang-chu","/manager/don-hang","/manager/tra-cuu-hang-loat","/manager/phan-cong","/manager/kho","/manager/canh-bao",
                "/manager/ho-so-van-hanh","/manager/khieu-nai","/manager/khieu-nai/{complaint}","/manager/doi-soat-cod","/manager/khuyen-mai","/manager/khuyen-mai/tao",
                "/manager/bao-cao","/manager/hieu-suat-shipper","/manager/bang-cuoc","/manager/lich-cuoc","/noi-bo/tai-khoan"};
        add(rows,"MANAGER",managerPages);add(rows,"ADMIN",managerPages);
        add(rows,"ADMIN","/admin/trang-chu","/admin/quan-ly-tai-khoan","/admin/chi-nhanh","/admin/chinh-sach-van-hanh","/admin/bang-cuoc",
                "/admin/cuoc-tuyen","/admin/an-toan-truy-cap","/admin/nhat-ky","/admin/lich-cuoc","/noi-bo/truy-cap-api");
        return rows.stream();
    }
    static void add(List<Arguments> rows,String role,String... paths) { for(String path:paths) rows.add(Arguments.of(role,path)); }
    @ParameterizedTest @MethodSource("pages")
    void everyRolePageRendersVietnameseWithoutTemplateErrors(String role,String path) throws Exception {
        path=path.replace("{order}",order.getId().toString()).replace("{complaint}",complaintId.toString());
        var request=get(path);if(!role.equals("GUEST")) request.with(authentication(actors.get(role)));
        var response=mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        String html=response.getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("\uFFFD"),path);assertTrue(html.contains("<title>"),path);
        assertTrue(html.matches("(?s).*<html[^>]*lang=\"vi\".*"),path);assertTrue(html.contains("charset=\"UTF-8\""),path);
        if(path.contains("/khieu-nai/")) {
            assertTrue(html.contains("ă â đ ê ô ơ ư"));assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("<script>alert('x')</script>"));
        }
    }
    static Stream<Arguments> forbiddenPages() {
        return Stream.of(Arguments.of("USER","/manager/kho"),Arguments.of("USER","/admin/quan-ly-tai-khoan"),Arguments.of("USER","/shipper/trang-chu"),
            Arguments.of("SHIPPER","/manager/phan-cong"),Arguments.of("SHIPPER","/admin/trang-chu"),Arguments.of("SHIPPER","/nguoi-dung/tao-don"),
            Arguments.of("MANAGER","/admin/chinh-sach-van-hanh"),Arguments.of("MANAGER","/shipper/trang-chu"),Arguments.of("ADMIN","/shipper/trang-chu"),Arguments.of("ADMIN","/nguoi-dung/tao-don"));
    }
    @ParameterizedTest @MethodSource("forbiddenPages")
    void rolesCannotOpenAnotherRolesRestrictedPages(String role,String path) throws Exception {
        mvc.perform(get(path).with(authentication(actors.get(role)))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"MISSING_RECEIPT","WRONG_AMOUNT","MISSING_PAID_AT"})
    void paidFlagAloneMustNotTellCustomerThatPaymentSucceeded(String defect) throws Exception {
        var payment=payments.findByOrder(order).orElseThrow();payment.setStatus(OrderPayment.PaymentStatus.PAID);payment.setPaidAt(LocalDateTime.now());
        if(defect.equals("MISSING_RECEIPT")) { payment.setMethod(OrderPayment.PaymentMethod.MOMO);payment.setTransactionRef(null); }
        else if(defect.equals("WRONG_AMOUNT")) payment.setAmount(payment.getAmount().add(java.math.BigDecimal.ONE));
        else payment.setPaidAt(null);
        payments.saveAndFlush(payment);
        String html=mvc.perform(get("/nguoi-dung/thanh-toan/"+order.getId()).with(authentication(actors.get("USER"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("cần tra soát"),defect);assertFalse(html.contains("<h5>Đã thanh toán</h5>"),defect);
        assertFalse(vn.edu.hcmute.uteexpress.util.PaymentReadiness.isReady(payment),defect);
    }
}
