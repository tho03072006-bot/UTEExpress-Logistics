package vn.edu.hcmute.uteexpress.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByTrackingCode(String trackingCode);

    /**
     * Tim don cua mot nguoi gui, co phan trang, ket hop loc trang thai va tim theo tu khoa.
     * Truyen null cho status hoac keyword nghia la khong ap dieu kien do.
     * Tu khoa tra trong 3 cho: ma van don, ten nguoi nhan, so dien thoai nguoi nhan.
     *
     * Viet bang @Query thay vi ten ham suy dien, vi ten ham khong dien ta duoc
     * "dieu kien chi ap khi tham so khac null".
     */
    @Query("""
            select o from Order o
            where o.sender = :sender
              and (:status is null or o.status = :status)
              and (:keyword is null
                   or lower(o.trackingCode) like lower(concat('%', :keyword, '%'))
                   or lower(o.receiverName) like lower(concat('%', :keyword, '%'))
                   or o.receiverPhone like concat('%', :keyword, '%'))
            """)
    Page<Order> searchOrdersOfSender(@Param("sender") AppUser sender,
                                     @Param("status") Order.OrderStatus status,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

    List<Order> findBySender(AppUser sender);

    Optional<Order> findByIdAndShipper(Long id, AppUser shipper);

    List<Order> findBySenderAndStatus(
            AppUser sender, Order.OrderStatus status);

    List<Order> findByShipper(AppUser shipper);

    List<Order> findByStatus(Order.OrderStatus status);
}