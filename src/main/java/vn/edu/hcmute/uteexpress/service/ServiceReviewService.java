package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.dto.ServiceReviewRequest;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nghiệp vụ đánh giá chất lượng dịch vụ giao hàng - TV1 phụ trách (việc 4).
 *
 * Chỉ người gửi của chính đơn đó mới được đánh giá, chỉ khi đơn đã giao thành công
 * (DELIVERED), và mỗi đơn chỉ đánh giá được một lần.
 */
public interface ServiceReviewService {

    /**
     * Kiểm tra quyền đánh giá rồi trả về đơn hàng tương ứng.
     * Ném IllegalStateException kèm lý do cụ thể nếu đơn không phải của người này,
     * chưa giao xong, hoặc đã đánh giá rồi.
     */
    Order requireReviewableOrder(Long orderId, String username);

    ServiceReview createReview(Long orderId, ServiceReviewRequest request, String username);

    Optional<ServiceReview> findByOrderId(Long orderId);

    /** Tra đánh giá theo id đơn, để trang lịch sử đơn biết đơn nào đã đánh giá rồi. */
    Map<Long, ServiceReview> findReviewsOfUser(String username);

    /** Vài đánh giá mới nhất toàn hệ thống, hiển thị ở trang chủ cho khách xem. */
    List<ServiceReview> findLatestReviews(int limit);
}
