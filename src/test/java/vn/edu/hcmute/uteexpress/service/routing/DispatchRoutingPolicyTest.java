package vn.edu.hcmute.uteexpress.service.routing;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DispatchRoutingPolicyTest {
    /** Đếm giống điều kiện cũ: chi tiết bắt đầu đúng bằng "Shipper {id}; " (không lẫn 1 với 12, bỏ dòng sai mẫu). */
    @Test void countsAssignmentsByExactShipperPrefix() {
        Map<String, Long> counts = DispatchRoutingPolicy.assignedToday(Arrays.asList(
                "Shipper 1; Gợi ý hệ thống", "Shipper 12; Phân công tay", "Shipper 1; Lần hai",
                "Shipper 1 thiếu dấu chấm phẩy", "shipper 1; chữ thường", "Ghi chú khác", null, "Shipper ; trống"));
        assertEquals(2L, counts.get("1"));
        assertEquals(1L, counts.get("12"));
        assertEquals(2, counts.size());
    }
}
