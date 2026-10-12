package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.entity.PromoCode;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentAttempt;

import java.math.BigDecimal;

import java.util.Map;
import java.util.Optional;

/**
 * Nghiệp vụ thanh toán cước vận chuyển - TV1 phụ trách (việc 3).
 *
 * COD chạy thật theo đúng nghĩa: lưu và hiển thị trạng thái chưa/đã thanh toán trong CSDL.
 * MoMo được xác nhận bằng API TEST hoặc IPN có chữ ký. VNPay chưa tích hợp.
 */
public interface OrderPaymentService {

    /**
     * Tạo bản ghi thanh toán ngay khi vận đơn được tạo.
     * Số tiền phải trả = cước của đơn trừ đi phần giảm giá (nếu có áp mã).
     *
     * @param promoCode      mã đã áp, null nếu đơn không dùng mã nào
     * @param discountAmount số tiền được giảm, null hoặc 0 nếu không giảm
     */
    OrderPayment createForOrder(Order order, OrderPayment.PaymentMethod method,
                                PromoCode promoCode, BigDecimal discountAmount);

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
     * API cũ được giữ để từ chối mọi xác nhận từ trình duyệt; không thể ghi PAID.
     */
    OrderPayment confirmWalletPayment(Long orderId, String username);

    /** Chốt một mã yêu cầu duy nhất dưới khóa DB; gọi cổng thanh toán sau khi commit. */
    MomoPaymentAttempt reserveMomoAttempt(Long orderId, String username, boolean qr);

    /** Snapshot của đúng giao dịch hiện tại, sau khi kiểm quyền chủ đơn. */
    MomoPaymentAttempt findOwnedMomoAttempt(Long orderId, String username);

    /** Chỉ gọi sau khi service MoMo đã xác thực phản hồi; khóa đơn rồi khóa thanh toán. */
    OrderPayment settleVerifiedMomo(Long orderId, String reference, long amount, String providerTransactionId);

    /**
     * Đánh dấu đã thu tiền COD. Dành cho lúc Shipper giao hàng thành công.
     * TODO (TV2): gọi hàm này khi cập nhật đơn sang trạng thái DELIVERED.
     */
    OrderPayment markCodCollected(Long orderId);
}
