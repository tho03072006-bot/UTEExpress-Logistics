package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;

import java.util.List;
import java.util.Optional;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {

    Optional<OrderPayment> findByOrder(Order order);

    /**
     * Lấy một lần toàn bộ thông tin thanh toán các đơn của người dùng, để trang lịch sử đơn
     * khỏi phải truy vấn lại cho từng dòng (tránh lỗi N+1 query).
     */
    List<OrderPayment> findByOrderSender(AppUser sender);

    long countByOrderSenderAndStatus(AppUser sender, OrderPayment.PaymentStatus status);
}
