package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;

import java.util.List;
import java.util.Optional;

public interface ServiceReviewRepository extends JpaRepository<ServiceReview, Long> {

    Optional<ServiceReview> findByOrder(Order order);

    boolean existsByOrder(Order order);

    List<ServiceReview> findByUserOrderByCreatedAtDesc(AppUser user);

    /** Danh sach danh gia moi nhat, dung cho khoi "Khach hang noi gi" o trang chu. */
    List<ServiceReview> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
