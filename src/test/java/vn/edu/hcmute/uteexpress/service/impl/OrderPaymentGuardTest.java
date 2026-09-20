package vn.edu.hcmute.uteexpress.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderPaymentRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra các trường hợp KHÔNG được phép thu tiền qua ví.
 *
 * Lỗi thật đã gặp: đơn đã huỷ nhưng trang danh sách vẫn hiện nút "Thanh toán", và
 * tầng Service cũng không chặn - nghĩa là người dùng trả tiền được cho một đơn sẽ
 * không bao giờ được giao. Bộ test này giữ cho lỗi đó không quay lại.
 */
@ExtendWith(MockitoExtension.class)
class OrderPaymentGuardTest {

    @Mock
    private OrderPaymentRepository orderPaymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AppUserRepository appUserRepository;

    private OrderPaymentServiceImpl orderPaymentService;

    private AppUser nguoiGui;

    @BeforeEach
    void setUp() {
        orderPaymentService = new OrderPaymentServiceImpl(
                orderPaymentRepository, orderRepository, appUserRepository);

        nguoiGui = new AppUser();
        nguoiGui.setId(1L);
        nguoiGui.setUsername("thotest");
    }

    private OrderPayment chuanBiDon(Order.OrderStatus trangThaiDon,
                                    OrderPayment.PaymentMethod phuongThuc) {
        Order order = new Order();
        order.setId(10L);
        order.setTrackingCode("UTE250076");
        order.setSender(nguoiGui);
        order.setStatus(trangThaiDon);

        OrderPayment payment = new OrderPayment();
        payment.setOrder(order);
        payment.setMethod(phuongThuc);
        payment.setStatus(OrderPayment.PaymentStatus.UNPAID);
        payment.setAmount(BigDecimal.valueOf(30000));

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderPaymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        return payment;
    }

    @Test
    @DisplayName("Không thu tiền đơn đã huỷ")
    void khongThuTienDonDaHuy() {
        chuanBiDon(Order.OrderStatus.CANCELLED, OrderPayment.PaymentMethod.MOMO);

        assertThatThrownBy(() -> orderPaymentService.confirmWalletPayment(10L, "thotest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã kết thúc");

        // Quan trọng hơn cả thông báo: không được ghi gì xuống cơ sở dữ liệu.
        verify(orderPaymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Không thu tiền đơn đã hoàn trả")
    void khongThuTienDonDaHoanTra() {
        chuanBiDon(Order.OrderStatus.RETURNED, OrderPayment.PaymentMethod.VNPAY);

        assertThatThrownBy(() -> orderPaymentService.confirmWalletPayment(10L, "thotest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã kết thúc");

        verify(orderPaymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đơn giao thất bại vẫn thanh toán được vì shipper còn giao lại")
    void donGiaoThatBaiVanThanhToanDuoc() {
        OrderPayment payment = chuanBiDon(Order.OrderStatus.FAILED, OrderPayment.PaymentMethod.MOMO);
        when(orderPaymentRepository.save(any())).thenAnswer(goi -> goi.getArgument(0));

        OrderPayment ketQua = orderPaymentService.confirmWalletPayment(10L, "thotest");

        assertThat(ketQua.getStatus()).isEqualTo(OrderPayment.PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("Đơn đang giao bình thường thì thanh toán được")
    void donDangGiaoThiThanhToanDuoc() {
        chuanBiDon(Order.OrderStatus.IN_TRANSIT, OrderPayment.PaymentMethod.VNPAY);
        when(orderPaymentRepository.save(any())).thenAnswer(goi -> goi.getArgument(0));

        OrderPayment ketQua = orderPaymentService.confirmWalletPayment(10L, "thotest");

        assertThat(ketQua.getStatus()).isEqualTo(OrderPayment.PaymentStatus.PAID);
        assertThat(ketQua.getTransactionRef()).isNotBlank();
    }
}
