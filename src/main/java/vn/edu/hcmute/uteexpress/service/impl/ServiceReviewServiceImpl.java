package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.ServiceReviewRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.repository.ServiceReviewRepository;
import vn.edu.hcmute.uteexpress.service.FileStorageService;
import vn.edu.hcmute.uteexpress.service.ServiceReviewService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class ServiceReviewServiceImpl implements ServiceReviewService {

    private final ServiceReviewRepository serviceReviewRepository;
    private final OrderRepository orderRepository;
    private final AppUserRepository appUserRepository;
    private final FileStorageService fileStorageService;

    public ServiceReviewServiceImpl(ServiceReviewRepository serviceReviewRepository,
                                    OrderRepository orderRepository,
                                    AppUserRepository appUserRepository,
                                    FileStorageService fileStorageService) {
        this.serviceReviewRepository = serviceReviewRepository;
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional(readOnly = true)
    public Order requireReviewableOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này"));

        if (!order.getSender().getUsername().equals(username)) {
            throw new IllegalStateException("Bạn không có quyền đánh giá đơn hàng này");
        }
        if (order.getStatus() != Order.OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Chỉ đánh giá được sau khi đơn đã giao thành công. Đơn này đang ở trạng thái "
                            + order.getStatus() + ".");
        }
        if (serviceReviewRepository.existsByOrder(order)) {
            throw new IllegalStateException("Đơn này bạn đã đánh giá rồi.");
        }
        return order;
    }

    @Override
    public ServiceReview createReview(Long orderId, ServiceReviewRequest request, String username) {
        Order order = requireReviewableOrder(orderId, username);
        AppUser reviewer = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng: " + username));

        ServiceReview review = new ServiceReview();
        review.setOrder(order);
        review.setUser(reviewer);
        review.setRating(request.getRating());
        review.setContent(request.getContent().trim());

        // Anh/video la tuy chon - khong dinh kem gi thi bo qua han buoc luu tep.
        if (request.getMediaFile() != null && !request.getMediaFile().isEmpty()) {
            review.setMediaType(fileStorageService.detectMediaType(request.getMediaFile()));
            review.setMediaPath(fileStorageService.storeReviewMedia(request.getMediaFile()));
        }

        review.setUpdatedAt(LocalDateTime.now());
        return serviceReviewRepository.save(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceReview> findByOrderId(Long orderId) {
        return orderRepository.findById(orderId).flatMap(serviceReviewRepository::findByOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ServiceReview> findReviewsOfUser(String username) {
        AppUser reviewer = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng: " + username));

        Map<Long, ServiceReview> byOrderId = new HashMap<>();
        for (ServiceReview review : serviceReviewRepository.findByUserOrderByCreatedAtDesc(reviewer)) {
            byOrderId.put(review.getOrder().getId(), review);
        }
        return byOrderId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReview> findLatestReviews(int limit) {
        return serviceReviewRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }
}
