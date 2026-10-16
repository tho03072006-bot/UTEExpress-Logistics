package vn.edu.hcmute.uteexpress.repository.manager;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.dto.manager.ShipperWorkloadResponse;

public interface ManagerDispatchRepository extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = "shipper")
    Page<Order> findByStatusOrderByCreatedAtAscIdAsc(Order.OrderStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockOrder(@Param("id") Long id);

    @Query("""
        select new vn.edu.hcmute.uteexpress.dto.manager.ShipperWorkloadResponse(u.id, u.username, u.fullName, count(o.id))
        from AppUser u left join Order o on o.shipper = u and o.status in :statuses
        where u.role = :role and u.enabled = true and u.lockedByAdmin = false
        group by u.id, u.username, u.fullName order by count(o.id), u.id
        """)
    Page<ShipperWorkloadResponse> findWorkloads(@Param("role") AppUser.Role role,
        @Param("statuses") List<Order.OrderStatus> statuses, Pageable pageable);

    @Query("select o.status as status, count(o.id) as total from Order o where o.createdAt >= :from and o.createdAt < :to group by o.status")
    List<StatusCount> countStatuses(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    interface StatusCount { Order.OrderStatus getStatus(); long getTotal(); }

    @Query("""
        select coalesce(sum(p.amount), 0) from OrderPayment p
        where p.status = vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentStatus.PAID
        and p.paidAt >= :from and p.paidAt < :to
        """)
    java.math.BigDecimal sumPaidFees(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

