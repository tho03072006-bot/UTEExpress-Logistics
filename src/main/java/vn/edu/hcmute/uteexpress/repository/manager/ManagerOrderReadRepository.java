package vn.edu.hcmute.uteexpress.repository.manager;

import java.time.LocalDateTime;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface ManagerOrderReadRepository extends Repository<Order, Long> {
    @EntityGraph(attributePaths = "shipper")
    @Query("select o from Order o where (:tracking = '' or o.trackingCode = :tracking) "
            + "and (:status is null or o.status = :status) "
            + "and o.createdAt >= :start and o.createdAt < :end")
    Page<Order> findOrders(@Param("tracking") String tracking, @Param("status") Order.OrderStatus status,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);
}

