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
        requireReviewer(username);
        if (orderId == null || orderId<=0) throw new IllegalArgumentException("ID đơn không hợp lệ.");
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này"));
        checkOrder(order,username);
        return order;
    }
    private void checkOrder(Order order,String username) {
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
    }

    @Override
    public ServiceReview createReview(Long orderId, ServiceReviewRequest request, String username) {
        AppUser reviewer = requireReviewer(username);
        if (orderId == null || orderId<=0 || request == null) throw new IllegalArgumentException("Dữ liệu đánh giá không hợp lệ.");
        String content = request.getContent() == null ? "" : request.getContent().strip();
        if (request.getRating() == null || request.getRating()<1 || request.getRating()>5
                || content.length()<50 || content.length()>1000
                || content.chars().anyMatch(c -> Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))
            throw new IllegalArgumentException("Số sao từ 1–5; nhận xét từ 50–1000 ký tự, không chứa ký tự điều khiển.");
        // Khóa đơn trước khi upload để hai tab không đồng thời tạo hai tệp cho một đánh giá.
        Order order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này"));
        checkOrder(order,username);

        ServiceReview review = new ServiceReview();
        review.setOrder(order);
        review.setUser(reviewer);
        review.setRating(request.getRating());
        review.setContent(content);

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
        if (limit<1 || limit>100) throw new IllegalArgumentException("Giới hạn đánh giá từ 1–100.");
        return serviceReviewRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }
    private AppUser requireReviewer(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Thiếu tài khoản đánh giá.");
        var reviewer = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Tài khoản không hợp lệ."));
        if (reviewer.getRole()!=AppUser.Role.USER || !reviewer.isEnabled() || reviewer.isLockedByAdmin())
            throw new org.springframework.security.access.AccessDeniedException("Chỉ khách hàng đang hoạt động được đánh giá.");
        return reviewer;
    }
}
