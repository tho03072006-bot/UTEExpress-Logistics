package vn.edu.hcmute.uteexpress.repository.manager;
import java.time.LocalDateTime;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse;
public interface OperationsOverviewRepository extends JpaRepository<Order, Long> {
    @Query("""
        select s from CodSettlement s join s.shipper u where s.status = vn.edu.hcmute.uteexpress.entity.CodSettlement.Status.CONFIRMED
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
          and """ + vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository.USER_SCOPE)
    Page<vn.edu.hcmute.uteexpress.entity.CodSettlement> lateCash(@Param("deadline") LocalDateTime deadline, @Param("branchId") Long branchId, Pageable pageable);
    /**
     * Điều kiện của từng nhóm cảnh báo cho đơn bí danh {@code o}; dùng chung cho trang danh sách và câu chỉ đếm.
     * "Chậm lấy" không tính đơn khách tự mang ra chi nhánh: không có Shipper nào phải đi lấy, đơn chỉ chờ khách tới quầy.
     * Các đơn đó, khi quá cùng hạn lấy hàng mà khách chưa mang tới, nằm ở nhóm riêng "Chờ khách mang tới quá hạn".
     * "Quá hạn giao" không nằm ở đây: hạn phụ thuộc từng tuyến nên service tính riêng (deliveryCandidates + DeliveryDeadlines).
     */
    String ALERT_FILTER = """
         (
        (:kind = 'PAYMENT_ISSUE' and o.status not in (vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.CANCELLED,
            vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.RETURNED)
            and (not exists (select missing.id from OrderPayment missing where missing.order = o)
                or exists (select payment.id from OrderPayment payment where payment.order = o
                    and (o.shippingFee is null or o.shippingFee < 0 or payment.amount is null or payment.amount < 0
                        or coalesce(payment.discountAmount,0) < 0 or coalesce(payment.discountAmount,0) > o.shippingFee
                        or payment.amount <> o.shippingFee - coalesce(payment.discountAmount,0)
                        or (payment.status = vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentStatus.PAID
                            and (payment.paidAt is null or (payment.method = vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentMethod.MOMO
                                and (payment.transactionRef is null or trim(payment.transactionRef) = ''))))
                        or (payment.method in (vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentMethod.MOMO,
                        vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentMethod.VNPAY)
                        and payment.status = vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentStatus.UNPAID)))))
        or
        (:kind = 'UNASSIGNED' and o.shipper is null and exists (select r.id from OrderRoute r where r.order = o and (
            (r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.PICKUP and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
              and r.pickupMode = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.COURIER and (:branchId is null or r.originBranch.id = :branchId))
            or (r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.DEST_HUB and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP and (:branchId is null or r.destBranch.id = :branchId)))))
        or (:kind = 'UNASSIGNED' and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and o.shipper is null
            and not exists (select r2.id from OrderRoute r2 where r2.order = o))
        or (:kind = 'LATE_PICKUP' and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and o.createdAt <= :pickup
            and not exists (select counter.id from OrderRoute counter where counter.order = o
                and counter.pickupMode = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.DROP_OFF))
        or (:kind = 'DROP_OFF_WAITING' and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and o.createdAt <= :pickup
            and exists (select waiting.id from OrderRoute waiting where waiting.order = o
                and waiting.pickupMode = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.DROP_OFF))
        or (:kind = 'RETRY_DUE' and (
        """ + vn.edu.hcmute.uteexpress.repository.OrderRepository.READY_RETRY + """
        ))
        or (:kind = 'MANY_FAILURES' and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED
            and (select count(h3.id) from OrderStatusHistory h3 where h3.order = o
            and h3.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED and h3.previousStatus <> h3.status) >= :maxAttempts)
        ) and """ + vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository.BRANCH_SCOPE;
    @Query("select o from Order o where " + ALERT_FILTER)
    Page<Order> alerts(@Param("kind") String kind, @Param("branchId") Long branchId, @Param("pickup") LocalDateTime pickup,
            @Param("now") LocalDateTime now, @Param("maxAttempts") int attempts, Pageable pageable);
    /** Chỉ đếm (trang tổng quan không cần danh sách đơn). */
    @Query("select count(o) from Order o where " + ALERT_FILTER)
    long countAlerts(@Param("kind") String kind, @Param("branchId") Long branchId, @Param("pickup") LocalDateTime pickup,
            @Param("now") LocalDateTime now, @Param("maxAttempts") int attempts);
    /**
     * Ứng viên "Quá hạn giao": đơn đang trên đường giao, tạo trước mốc hạn ngắn nhất. Hạn thật của từng đơn phụ thuộc
     * thời gian dự kiến của tuyến (văn bản do Admin cấu hình) nên service xét tiếp từng đơn; xem DeliveryDeadlines.
     */
    @Query("""
        select o from Order o where o.status in (vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP,
            vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.IN_TRANSIT, vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED)
          and o.createdAt <= :cutoff and """ + vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository.BRANCH_SCOPE
          + " order by o.createdAt, o.id")
    java.util.List<Order> deliveryCandidates(@Param("cutoff") LocalDateTime cutoff, @Param("branchId") Long branchId);
    @Query("""
        select new vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse(u.id, u.username, u.fullName,
          sum(case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP then 1 else 0 end),
          sum(case when o.status in (vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP,
               vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.IN_TRANSIT) then 1 else 0 end),
          sum(case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.DELIVERED then 1 else 0 end),
          sum(case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED then 1 else 0 end),
          sum(case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.RETURNED then 1 else 0 end),
          (select count(p) from Order p where p.status not in (vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP,
               vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.CANCELLED)
             and (exists (select r.id from vn.edu.hcmute.uteexpress.entity.routing.OrderRoute r where r.order = p and r.pickupShipper = u)
               or (p.shipper = u and not exists (select r2.id from vn.edu.hcmute.uteexpress.entity.routing.OrderRoute r2 where r2.order = p)))))
        from AppUser u left join Order o on o.shipper = u where u.role = vn.edu.hcmute.uteexpress.entity.AppUser.Role.SHIPPER
          and """ + vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository.USER_SCOPE + """
        group by u.id, u.username, u.fullName order by u.username, u.id
        """)
    Page<ShipperPerformanceResponse> performance(@Param("branchId") Long branchId, Pageable pageable);
    String LATE_COMPLAINT_FILTER = """
        c.status in (vn.edu.hcmute.uteexpress.entity.OrderComplaint.Status.OPEN,
          vn.edu.hcmute.uteexpress.entity.OrderComplaint.Status.IN_PROGRESS)
          and ((c.managerReply is null and c.createdAt <= :response) or c.createdAt <= :resolve)
          and """ + vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository.BRANCH_SCOPE;
    @Query("select c from OrderComplaint c join c.order o where " + LATE_COMPLAINT_FILTER)
    Page<vn.edu.hcmute.uteexpress.entity.OrderComplaint> lateComplaints(@Param("response") LocalDateTime response,
            @Param("resolve") LocalDateTime resolve, @Param("branchId") Long branchId, Pageable pageable);
    @Query("select count(c) from OrderComplaint c join c.order o where " + LATE_COMPLAINT_FILTER)
    long countLateComplaints(@Param("response") LocalDateTime response, @Param("resolve") LocalDateTime resolve, @Param("branchId") Long branchId);
}
