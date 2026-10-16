package vn.edu.hcmute.uteexpress.repository.manager;
import java.time.LocalDateTime;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse;
public interface OperationsOverviewRepository extends JpaRepository<Order, Long> {
    @Query("""
        select s from CodSettlement s where s.status = vn.edu.hcmute.uteexpress.entity.CodSettlement.Status.CONFIRMED
          and s.reviewedAt <= :deadline and
          ((s.totalAmount > s.declaredAmount and s.totalAmount - s.declaredAmount >
             (select coalesce(sum(c.amount),0) from OperationsCase c where c.settlement = s
              and c.kind = vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Kind.CASH_SUPPLEMENT
              and c.status in (vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Status.APPROVED,
                               vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Status.COMPLETED)))
           or (s.declaredAmount > s.totalAmount and s.declaredAmount - s.totalAmount >
             (select coalesce(sum(c2.amount),0) from OperationsCase c2 where c2.settlement = s
              and c2.kind = vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Kind.CASH_REFUND
              and c2.status = vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Status.COMPLETED)))
        """)
    Page<vn.edu.hcmute.uteexpress.entity.CodSettlement> lateCash(@Param("deadline") LocalDateTime deadline, Pageable pageable);
    @Query("""
        select o from Order o where
        (:kind = 'UNASSIGNED' and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and o.shipper is null)
        or (:kind = 'LATE_PICKUP' and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and o.createdAt <= :pickup)
        or (:kind = 'LATE_DELIVERY' and o.status in (vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP,
            vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.IN_TRANSIT, vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED)
            and ((o.serviceType = vn.edu.hcmute.uteexpress.entity.Order.ServiceType.STANDARD and o.createdAt <= :standard)
              or (o.serviceType = vn.edu.hcmute.uteexpress.entity.Order.ServiceType.EXPRESS and o.createdAt <= :express)
              or (o.serviceType = vn.edu.hcmute.uteexpress.entity.Order.ServiceType.SUPER_EXPRESS and o.createdAt <= :superExpress)))
        or (:kind = 'RETRY_DUE' and (
        """ + vn.edu.hcmute.uteexpress.repository.OrderRepository.READY_RETRY + """
        ))
        or (:kind = 'MANY_FAILURES' and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED
            and (select count(h3.id) from OrderStatusHistory h3 where h3.order = o
            and h3.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED) >= :maxAttempts)
        """)
    Page<Order> alerts(@Param("kind") String kind, @Param("pickup") LocalDateTime pickup,
            @Param("standard") LocalDateTime standard, @Param("express") LocalDateTime express,
            @Param("superExpress") LocalDateTime superExpress, @Param("now") LocalDateTime now,
            @Param("maxAttempts") int attempts, Pageable pageable);
    @Query("""
        select new vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse(u.id, u.username, u.fullName,
          sum(case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP then 1 else 0 end),
          sum(case when o.status in (vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP,
               vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.IN_TRANSIT) then 1 else 0 end),
          sum(case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.DELIVERED then 1 else 0 end),
          sum(case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED then 1 else 0 end),
          sum(case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.RETURNED then 1 else 0 end))
        from AppUser u left join Order o on o.shipper = u where u.role = vn.edu.hcmute.uteexpress.entity.AppUser.Role.SHIPPER
        group by u.id, u.username, u.fullName order by u.username, u.id
        """)
    Page<ShipperPerformanceResponse> performance(Pageable pageable);
    @Query("""
        select c from OrderComplaint c where c.status in (vn.edu.hcmute.uteexpress.entity.OrderComplaint.Status.OPEN,
          vn.edu.hcmute.uteexpress.entity.OrderComplaint.Status.IN_PROGRESS)
          and ((c.managerReply is null and c.createdAt <= :response) or c.createdAt <= :resolve)
        """)
    Page<vn.edu.hcmute.uteexpress.entity.OrderComplaint> lateComplaints(@Param("response") LocalDateTime response,
            @Param("resolve") LocalDateTime resolve, Pageable pageable);
}
