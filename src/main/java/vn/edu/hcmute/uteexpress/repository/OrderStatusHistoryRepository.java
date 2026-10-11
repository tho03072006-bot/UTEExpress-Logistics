package vn.edu.hcmute.uteexpress.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderStatusHistory;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select h from OrderStatusHistory h where h.order.id in :ids
              and h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED
              and h.id = (select max(h2.id) from OrderStatusHistory h2 where h2.order = h.order
                and h2.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED)
            """)
    java.util.List<OrderStatusHistory> latestFailures(@org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);

    @org.springframework.data.jpa.repository.Query("""
            select new vn.edu.hcmute.uteexpress.dto.tracking.ShipperActivity(h.order.id, h.status, h.occurredAt)
            from OrderStatusHistory h where h.actor = :actor and h.previousStatus <> h.status
              and h.occurredAt >= :from and h.occurredAt < :to order by h.occurredAt, h.id
            """)
    java.util.List<vn.edu.hcmute.uteexpress.dto.tracking.ShipperActivity> activities(
            @org.springframework.data.repository.query.Param("actor") vn.edu.hcmute.uteexpress.entity.AppUser actor,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);
    Page<OrderStatusHistory> findByOrder(Order order, Pageable pageable);
    long countByOrderAndStatus(Order order, Order.OrderStatus status);
    Optional<OrderStatusHistory> findFirstByOrderAndPreviousStatusAndStatusOrderByOccurredAtDescIdDesc(
            Order order, Order.OrderStatus previousStatus, Order.OrderStatus status);
}
