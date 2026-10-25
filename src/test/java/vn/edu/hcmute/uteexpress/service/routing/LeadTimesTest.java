package vn.edu.hcmute.uteexpress.service.routing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import static org.junit.jupiter.api.Assertions.*;

class LeadTimesTest {
    @Test void readsEverySeededLeadTimeAsItsUpperBound() {
        assertEquals(4, LeadTimes.hours("Trong 4 giờ").getAsInt());
        assertEquals(24, LeadTimes.hours("Trong ngày").getAsInt());
        assertEquals(48, LeadTimes.hours("1–2 ngày").getAsInt());
        assertEquals(48, LeadTimes.hours("2 ngày").getAsInt());
        assertEquals(72, LeadTimes.hours("2–3 ngày").getAsInt());
        assertEquals(96, LeadTimes.hours("3-4 ngày").getAsInt(), "Gạch nối thường cũng được");
        assertEquals(120, LeadTimes.hours("  4–5   NGÀY ").getAsInt());
        assertEquals(6, LeadTimes.hours("2–6 giờ").getAsInt());
    }

    @Test void rejectsTextThatHasNoReadableDeadline() {
        for (String value : new String[]{null, "", "Sớm nhất có thể", "0 ngày", "3–2 ngày", "31 ngày", "khoảng 2 ngày", "2 tuần"})
            assertTrue(LeadTimes.hours(value).isEmpty(), String.valueOf(value));
    }

    @Test void routeLeadTimeWinsAndLegacyOrdersFallBackToPolicy() {
        var sgn = branch("SGN", 1L); var han = branch("HAN", 2L);
        var policy = new OperationsPolicy(); // Tiêu chuẩn 120 giờ, Nhanh 48 giờ
        var rules = new DeliveryDeadlines.Rules(policy, Map.of(new DeliveryDeadlines.Key(1L, 2L, Order.ServiceType.EXPRESS), 72));
        var order = new Order(); order.setServiceType(Order.ServiceType.EXPRESS); order.setCreatedAt(LocalDateTime.of(2026, 10, 20, 8, 0));
        var route = new OrderRoute(order, area("SGN-Q1", sgn), area("HAN-CAUGIAY", han));
        assertEquals(72, rules.hours(order, route));
        assertEquals(LocalDateTime.of(2026, 10, 23, 8, 0), rules.due(order, route));
        assertEquals(48, rules.hours(order, null), "Đơn cũ chưa có tuyến dùng Chính sách vận hành");
        order.setServiceType(Order.ServiceType.STANDARD);
        assertEquals(120, rules.hours(order, route), "Tuyến không có biểu giá đọc được cũng dùng Chính sách vận hành");
        assertEquals(4, rules.shortest());
    }

    private static Branch branch(String code, Long id) {
        var branch = new Branch(code, code, code, "Địa chỉ kho", code + "-HUB", LocalDateTime.now());
        ReflectionTestUtils.setField(branch, "id", id);
        return branch;
    }

    private static ProvinceArea area(String code, Branch branch) {
        return new ProvinceArea("79", code, code, branch, BigDecimal.ONE, BigDecimal.ONE);
    }
}
