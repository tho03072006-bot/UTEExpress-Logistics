package vn.edu.hcmute.uteexpress.validation;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Gửi dữ liệu xấu vào MỌI form/tham số của ứng dụng (tự đọc danh sách đường dẫn từ Spring MVC):
 * chuỗi rỗng, chỉ khoảng trắng, quá dài, ký tự điều khiển, emoji, số âm/NaN/vô cực/tràn số, ngày sai,
 * enum lạ, tệp rỗng/giả ảnh/quá lớn/tên tệp leo thư mục. Yêu cầu: không đường dẫn nào trả lỗi 500
 * hay ném ngoại lệ ra ngoài; lỗi nhập liệu phải được báo lại cho người dùng. Chạy trên H2 riêng.
 */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:input-fuzz-test;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload.dir=target/test-proof-uploads", "app.mail.order-updates.enabled=false",
    // Khóa giả chỉ dùng cho test: bật JWT để /api/internal/** được thử thật, không phụ thuộc config/jwt-local.properties (CI không có).
    "app.jwt.api.enabled=true", "app.jwt.api.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("isolated-logistics")
class InputFuzzIntegrationTest {
    private static final int PROFILES = 8;
    private static final Set<String> SKIP = Set.of("/logout", "/dang-xuat", "/api/assistant/messages");

    @Autowired MockMvc mvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired StaffAccountRepository staff;
    AppUser customer, shipper;
    Authentication managerAuth, adminAuth;
    Order order;

    @BeforeEach void seed() {
        customer = account("fuzz-customer", AppUser.Role.USER);
        shipper = account("fuzz-shipper", AppUser.Role.SHIPPER);
        managerAuth = staffAuth(account("fuzz-manager", AppUser.Role.MANAGER));
        adminAuth = staffAuth(account("fuzz-admin", AppUser.Role.ADMIN));
        order = new Order(); order.setTrackingCode("UTE-FUZZ-01"); order.setSender(customer); order.setShipper(shipper);
        order.setSenderName("Người gửi"); order.setReceiverName("Người nhận"); order.setReceiverPhone("0901234567");
        order.setSenderAddress("1 Võ Văn Ngân, Thủ Đức"); order.setReceiverAddress("2 Lê Văn Việt, Thủ Đức"); order.setWeightKg(1.0);
        order.setShippingFee(new BigDecimal("18000")); order.setStatus(Order.OrderStatus.PICKED_UP);
        order.setCreatedAt(LocalDateTime.now().minusDays(1)); order.setUpdatedAt(LocalDateTime.now().minusDays(1));
        order = orders.saveAndFlush(order);
    }

    @Test void noEndpointCrashesOnMalformedInput() throws Exception {
        List<String> failures = new ArrayList<>();
        Map<String, Integer> statuses = new TreeMap<>();
        int requests = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            var info = entry.getKey();
            var methods = info.getMethodsCondition().getMethods();
            boolean post = methods.stream().anyMatch(m -> m.name().equals("POST"));
            boolean get = methods.isEmpty() || methods.stream().anyMatch(m -> m.name().equals("GET"));
            var params = inputs(entry.getValue());
            if (!post && (!get || params.isEmpty())) continue;
            for (String pattern : info.getPatternValues()) {
                if (SKIP.contains(pattern) || pattern.startsWith("/error")) continue;
                for (int profile = 0; profile < PROFILES; profile++) {
                    String path = fillPath(pattern, profile);
                    boolean multipart = params.stream().anyMatch(p -> MultipartFile.class.isAssignableFrom(p.type()));
                    MockHttpServletRequestBuilder request;
                    if (post && multipart) {
                        MockMultipartHttpServletRequestBuilder mp = multipart(path);
                        for (var p : params) if (MultipartFile.class.isAssignableFrom(p.type())) mp.file(file(p.name(), profile));
                        request = mp;
                    } else request = post ? post(path) : get(path);
                    for (var p : params) {
                        if (MultipartFile.class.isAssignableFrom(p.type())) continue;
                        for (String value : values(p, profile)) request.param(p.name(), value);
                    }
                    request.servletPath(path).with(csrf());
                    request = authenticate(request, pattern);
                    requests++;
                    try {
                        int status = mvc.perform(request).andReturn().getResponse().getStatus();
                        statuses.merge(status + " " + (post ? "POST " : "GET ") + pattern, 1, Integer::sum);
                        if (status >= 500) failures.add(status + " " + (post ? "POST " : "GET ") + pattern + " [kiểu dữ liệu xấu #" + profile + "]");
                    } catch (Exception ex) {
                        Throwable root = ex; while (root.getCause() != null && root.getCause() != root) root = root.getCause();
                        failures.add("NGOẠI LỆ " + (post ? "POST " : "GET ") + pattern + " [#" + profile + "]: " + root.getClass().getSimpleName() + " " + root.getMessage());
                    }
                }
            }
        }
        // Chạy với -DargLine=-Dfuzz.debug=true để xem mỗi đường dẫn trả mã nào (giúp soát trang báo 400 thay vì báo lỗi tại ô nhập).
        if (Boolean.getBoolean("fuzz.debug")) statuses.forEach((k, v) -> System.out.println("FUZZ " + k + " x" + v));
        assertTrue(requests > 200, "Quá ít yêu cầu được thử: " + requests);
        assertTrue(failures.isEmpty(), "Các đường dẫn bị lỗi khi nhận dữ liệu xấu:\n" + String.join("\n", new TreeSet<>(failures)));
    }

    /* ---------- Tham số của từng hàm xử lý ---------- */

    record Input(String name, Class<?> type, Class<?> element) { }

    private static List<Input> inputs(HandlerMethod handler) {
        List<Input> result = new ArrayList<>();
        for (MethodParameter parameter : handler.getMethodParameters()) {
            parameter.initParameterNameDiscovery(new org.springframework.core.DefaultParameterNameDiscoverer());
            Class<?> type = parameter.getParameterType();
            if (parameter.hasParameterAnnotation(PathVariable.class) || parameter.hasParameterAnnotation(RequestBody.class)
                    || parameter.hasParameterAnnotation(RequestHeader.class) || parameter.hasParameterAnnotation(CookieValue.class)) continue;
            var requestParam = parameter.getParameterAnnotation(RequestParam.class);
            if (requestParam != null || MultipartFile.class.isAssignableFrom(type)) {
                String name = requestParam != null && !requestParam.name().isEmpty() ? requestParam.name()
                        : requestParam != null && !requestParam.value().isEmpty() ? requestParam.value() : parameter.getParameterName();
                if (name == null || Map.class.isAssignableFrom(type)) continue;
                result.add(new Input(name, type, element(parameter)));
            } else if (parameter.hasParameterAnnotation(ModelAttribute.class)
                    || (!BeanUtils.isSimpleProperty(type) && type.getName().startsWith("vn.edu.hcmute"))) {
                for (Field field : allFields(type))
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) result.add(new Input(field.getName(), field.getType(), null));
            }
        }
        return result;
    }

    private static Class<?> element(MethodParameter parameter) {
        var generic = parameter.getGenericParameterType();
        if (generic instanceof java.lang.reflect.ParameterizedType pt && pt.getActualTypeArguments()[0] instanceof Class<?> c) return c;
        return String.class;
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) fields.addAll(Arrays.asList(c.getDeclaredFields()));
        return fields;
    }

    /* ---------- Giá trị xấu theo kiểu dữ liệu ---------- */

    private static List<String> values(Input input, int profile) {
        Class<?> type = input.type();
        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            return switch (profile) {
                case 0 -> List.of("");
                case 1 -> List.of("abc", "1");
                case 2 -> java.util.stream.IntStream.range(0, 600).mapToObj(Integer::toString).toList();
                case 3 -> List.of("-1", "-1");
                default -> List.of(String.valueOf(Long.MAX_VALUE), "99999999999999999999");
            };
        }
        return List.of(scalar(type, profile));
    }

    private static String scalar(Class<?> type, int profile) {
        if (type == boolean.class || type == Boolean.class) return List.of("", "maybe", "true", "1", "TRUE ", "no", "x".repeat(300), "\u0000").get(profile);
        if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != char.class))
            return List.of("", "   ", "99999999999999999999999", "NaN", "Infinity", "-1", "abc", "1e309").get(profile);
        if (type.isEnum()) return List.of("", "   ", "X".repeat(300), "NOT_A_VALUE", "0", "-1", "<b>", "\u0000").get(profile);
        if (type == LocalDate.class) return List.of("", "2026-02-30", "9999-12-31", "0001-01-01", "abc", "2026-13-01", "-5", "1900-01-01").get(profile);
        if (type == LocalDateTime.class) return List.of("", "2026-10-21T25:61", "9999-12-31T23:59", "0001-01-01T00:00", "abc", "2026-10-21", "-5", "1900-01-01T00:00").get(profile);
        return List.of("", "   ", "x".repeat(5000), "a\u0000b\u0007c", "<script>alert(1)</script>", "😀".repeat(300), "' OR 1=1 --", "-1").get(profile);
    }

    private static MockMultipartFile file(String name, int profile) {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        return switch (profile) {
            case 0 -> new MockMultipartFile(name, "", "application/octet-stream", new byte[0]);
            case 1 -> new MockMultipartFile(name, "anh.png", "image/png", "khong-phai-anh".getBytes());
            case 2 -> new MockMultipartFile(name, "lon.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);
            case 3 -> new MockMultipartFile(name, "../../../evil.png", "image/png", png);
            case 4 -> new MockMultipartFile(name, "anh.exe", "application/x-msdownload", png);
            case 5 -> new MockMultipartFile(name, "😀".repeat(100) + ".png", "image/png", png);
            case 6 -> new MockMultipartFile(name, "anh.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes());
            default -> new MockMultipartFile(name, "anh.png", "image/png", png);
        };
    }

    private String fillPath(String pattern, int profile) {
        String id = switch (profile) { case 1 -> "999999"; case 2 -> "-1"; case 3 -> "abc"; default -> order.getId().toString(); };
        return pattern.replaceAll("\\{type}", profile == 3 ? "KHONG_CO" : "STANDARD").replaceAll("\\{[^}]+}", id);
    }

    private MockHttpServletRequestBuilder authenticate(MockHttpServletRequestBuilder request, String pattern) {
        if (pattern.startsWith("/admin")) return request.with(authentication(adminAuth));
        if (pattern.startsWith("/manager") || pattern.startsWith("/noi-bo")) return request.with(authentication(managerAuth));
        if (pattern.startsWith("/shipper")) return request.with(user(shipper.getUsername()).roles("SHIPPER"));
        if (pattern.startsWith("/nguoi-dung") || pattern.startsWith("/api")) return request.with(user(customer.getUsername()).roles("USER"));
        return request;
    }

    private AppUser account(String username, AppUser.Role role) {
        var user = new AppUser(username, "test-hash", username + "@test.invalid");
        user.setRole(role); user.setEnabled(true); user.setFullName("Nguyễn Văn Thử"); user.setPhone("0901234567");
        return users.saveAndFlush(user);
    }

    private Authentication staffAuth(AppUser user) {
        var account = new StaffAccount(); account.setUser(user); account.setActive(true);
        account.setPasswordChangeRequired(false); account.setIssuedBy("test-only");
        staff.saveAndFlush(account);
        var principal = new StaffPrincipal(account);
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }
}
