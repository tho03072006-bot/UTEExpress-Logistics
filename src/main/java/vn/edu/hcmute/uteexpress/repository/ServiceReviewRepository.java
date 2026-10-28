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

    /** Số lượt và điểm trung bình khách chấm cho các đơn do Shipper này giao (lấy từ đánh giá dịch vụ có sẵn). */
    @org.springframework.data.jpa.repository.Query(
            "select count(r), coalesce(avg(r.rating), 0) from ServiceReview r where r.order.shipper = :shipper")
    List<Object[]> ratingSummaryForShipper(@org.springframework.data.repository.query.Param("shipper") AppUser shipper);

    /** Nhận xét mới nhất về các đơn do Shipper này giao. */
    @org.springframework.data.jpa.repository.Query(
            "select r from ServiceReview r join fetch r.order o where o.shipper = :shipper order by r.createdAt desc, r.id desc")
    List<ServiceReview> latestForShipper(@org.springframework.data.repository.query.Param("shipper") AppUser shipper, Pageable pageable);
}
