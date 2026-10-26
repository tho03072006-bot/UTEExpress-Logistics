package vn.edu.hcmute.uteexpress.dto.profile;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileDetailFormTest {
    /** Gợi ý tỉnh/thành theo bản đồ cũ, cùng hệ với khu vực giao nhận (địa danh trước 01/07/2025). */
    @Test void provinceSuggestionsFollowTheOldSixtyThreeProvinceMap() {
        var provinces = ProfileDetailForm.PROVINCES;
        assertEquals(63, provinces.size());
        assertEquals(63, new HashSet<>(provinces).size(), "Không trùng tên");
        assertTrue(provinces.containsAll(List.of("TP. Hồ Chí Minh", "Hà Nội", "Đà Nẵng", "Bình Dương", "Bà Rịa - Vũng Tàu",
                "Quảng Nam", "Thừa Thiên Huế", "Hà Giang", "Yên Bái")));
        assertFalse(provinces.contains("Huế"), "Tên mới sau sáp nhập không dùng nữa");
    }
}
