package vn.edu.hcmute.uteexpress.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.entity.Order;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra bảng cước dùng cho trang "Ước tính cước phí" của khách chưa đăng nhập.
 *
 * Không cần database: phép tính cước là công thức thuần, các tham số phụ thuộc đều
 * truyền null được vì đường chạy này không chạm tới repository nào.
 */
class OrderFeeEstimateTest {

    private final OrderServiceImpl orderService =
            new OrderServiceImpl(null, null, null, null, null);

    @Test
    @DisplayName("Trả về đủ ba loại dịch vụ, đúng thứ tự tiêu chuẩn - nhanh - hoả tốc")
    void traVeDuBaLoaiDungThuTu() {
        Map<Order.ServiceType, BigDecimal> bangCuoc = orderService.estimateAllServices(1);

        assertThat(bangCuoc).hasSize(3);
        assertThat(bangCuoc.keySet()).containsExactly(
                Order.ServiceType.STANDARD,
                Order.ServiceType.EXPRESS,
                Order.ServiceType.SUPER_EXPRESS);
    }

    @Test
    @DisplayName("Cước bằng phí cơ bản cộng 3.000đ mỗi kilôgam")
    void tinhDungTheoCongThuc() {
        // 2 kg: tiêu chuẩn 15.000 + 6.000, nhanh 25.000 + 6.000, hoả tốc 40.000 + 6.000
        Map<Order.ServiceType, BigDecimal> bangCuoc = orderService.estimateAllServices(2);

        assertThat(bangCuoc.get(Order.ServiceType.STANDARD)).isEqualByComparingTo("21000");
        assertThat(bangCuoc.get(Order.ServiceType.EXPRESS)).isEqualByComparingTo("31000");
        assertThat(bangCuoc.get(Order.ServiceType.SUPER_EXPRESS)).isEqualByComparingTo("46000");
    }

    @Test
    @DisplayName("Khối lượng lẻ vẫn tính đúng, không làm tròn mất tiền")
    void khoiLuongLeVanDung() {
        // 2,5 kg tiêu chuẩn: 15.000 + 7.500 = 22.500
        assertThat(orderService.estimateAllServices(2.5).get(Order.ServiceType.STANDARD))
                .isEqualByComparingTo("22500");
    }

    @Test
    @DisplayName("Bảng cước khớp với hàm tính cước lúc tạo đơn thật")
    void khopVoiCuocLucTaoDonThat() {
        // Hai đường phải cho cùng kết quả, nếu lệch thì khách xem giá một đằng
        // rồi tạo đơn bị tính một nẻo.
        Map<Order.ServiceType, BigDecimal> bangCuoc = orderService.estimateAllServices(3.2);

        for (Order.ServiceType serviceType : Order.ServiceType.values()) {
            assertThat(bangCuoc.get(serviceType))
                    .isEqualByComparingTo(orderService.estimateFee(serviceType, 3.2));
        }
    }
}
