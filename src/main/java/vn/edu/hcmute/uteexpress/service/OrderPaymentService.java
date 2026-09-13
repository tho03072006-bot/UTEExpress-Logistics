package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;

import java.util.Map;
import java.util.Optional;

/**
 * Nghiệp vụ thanh toán cước vận chuyển - TV1 phụ trách (việc 3).
 *
 * COD chạy thật theo đúng nghĩa: lưu và hiển thị trạng thái chưa/đã thanh toán trong CSDL.
 * VNPay / MoMo là bản MÔ PHỎNG - đồ án môn học không có API key thật của cổng thanh toán,
 * nên không có lời gọi nào ra ngoài Internet; người dùng đi qua một trang giả lập
 * rồi bấm xác nhận để diễn lại đúng luồng nghiệp vụ.
 */
public interface OrderPaymentService {

    /** Tạo bản ghi thanh toán ngay khi vận đơn được tạo, số tiền lấy theo cước của đơn. */
    OrderPayment createForOrder(Order order, OrderPayment.PaymentMethod method);

    Optional<OrderPayment> findByOrder(Order order);

    /** Số đơn còn nợ cước, dùng cho thẻ thống kê ở trang tổng quan. */
    long countUnpaidOfUser(String username);

    /** Tra thông tin thanh toán theo id đơn, dùng để hiển thị ở danh sách lịch sử đơn. */
    Map<Long, OrderPayment> findPaymentsOfUser(String username);

    /**
     * Lấy thông tin thanh toán của một đơn và bảo đảm đơn đó thuộc về người đang đăng nhập.
     * Ném IllegalStateException nếu đơn không tồn tại hoặc là đơn của người khác.
     */
    OrderPayment findOwnedPayment(Long orderId, String username);

    /**
     * Xác nhận đã thanh toán ở trang ví điện tử giả lập.
     * Ném IllegalStateException nếu đơn là COD (COD thu tiền lúc giao, không trả trước)
     * hoặc đơn đã thanh toán rồi.
     */
    OrderPayment confirmWalletPayment(Long orderId, String username);

    /**
     * Đánh dấu đã thu tiền COD. Dành cho lúc Shipper giao hàng thành công.
     * TODO (TV2): gọi hàm này khi cập nhật đơn sang trạng thái DELIVERED.
     */
    OrderPayment markCodCollected(Long orderId);
}
