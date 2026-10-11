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

    long countByShipperAndStatus(AppUser shipper, Order.OrderStatus status);

    @Query("""
            select o from Order o where o.shipper = :shipper
              and (:status is null or o.status = :status)
              and (:keyword is null or lower(o.trackingCode) like lower(concat('%', :keyword, '%')) escape '!'
                   or lower(o.receiverName) like lower(concat('%', :keyword, '%')) escape '!'
                   or o.receiverPhone like concat('%', :keyword, '%') escape '!')
              and (:from is null or o.createdAt >= :from) and (:to is null or o.createdAt < :to)
              and ((:work = 1 and (o.status in (vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP,
                    vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.IN_TRANSIT)
                    or (o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and not exists (
                      select later.id from OrderStatusHistory later where later.order = o and later.nextAttemptAt >= :tomorrow
                        and later.id = (select max(l2.id) from OrderStatusHistory l2 where l2.order = o and l2.failureReason is not null
                          and l2.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                          and l2.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP)))))
                   or (:work = 2 and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                       and (select count(pc.id) from OrderStatusHistory pc where pc.order = o and pc.failureReason is not null
                         and pc.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                         and pc.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP) < 3
                       and exists (select p.id from OrderStatusHistory p where p.order = o and p.nextAttemptAt <= :now
                         and p.id = (select max(p2.id) from OrderStatusHistory p2 where p2.order = o and p2.failureReason is not null
                           and p2.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                           and p2.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP)))
                   or (o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED
                       and (select count(attempt.id) from OrderStatusHistory attempt where attempt.order = o
                         and attempt.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED) < 3
                       and exists (
                       select h.id from OrderStatusHistory h where h.order = o
                         and h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED
                         and h.id = (select max(h2.id) from OrderStatusHistory h2 where h2.order = o
                           and h2.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED)
                         and ((:work = 1 and h.nextAttemptAt < :tomorrow) or (:work = 2 and h.nextAttemptAt <= :now)))))
            order by case when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED and exists (
                       select due.id from OrderStatusHistory due where due.order = o and due.nextAttemptAt <= :now
                         and due.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED
                         and due.id = (select max(latest.id) from OrderStatusHistory latest where latest.order = o
                           and latest.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED)) then 0
                     when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and exists (
                       select q.id from OrderStatusHistory q where q.order = o and q.nextAttemptAt <= :now
                         and q.id = (select max(q2.id) from OrderStatusHistory q2 where q2.order = o and q2.failureReason is not null
                           and q2.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                           and q2.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP)) then 0
                     when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.IN_TRANSIT then 1
                     when o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED then 2 else 3 end,
                     case when o.serviceType = vn.edu.hcmute.uteexpress.entity.Order.ServiceType.SUPER_EXPRESS then 0
                     when o.serviceType = vn.edu.hcmute.uteexpress.entity.Order.ServiceType.EXPRESS then 1 else 2 end,
                     o.createdAt, o.id
            """)
    Page<Order> searchWorkOrders(@Param("shipper") AppUser shipper, @Param("status") Order.OrderStatus status,
            @Param("keyword") String keyword, @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to, @Param("work") int work,
            @Param("now") java.time.LocalDateTime now, @Param("tomorrow") java.time.LocalDateTime tomorrow, Pageable pageable);

    @Query("""
            select count(o) from Order o where o.shipper = :shipper
            and ((o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED
            and (select count(attempt.id) from OrderStatusHistory attempt where attempt.order = o
              and attempt.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED) < 3 and exists (
              select h.id from OrderStatusHistory h where h.order = o and h.nextAttemptAt <= :now
                and h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED
                and h.id = (select max(h2.id) from OrderStatusHistory h2 where h2.order = o
                  and h2.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED)))
            or (o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
              and (select count(pc.id) from OrderStatusHistory pc where pc.order = o and pc.failureReason is not null
                and pc.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                and pc.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP) < 3
              and exists (select p.id from OrderStatusHistory p where p.order = o and p.nextAttemptAt <= :now
                and p.id = (select max(p2.id) from OrderStatusHistory p2 where p2.order = o and p2.failureReason is not null
                  and p2.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                  and p2.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP))))
            """)
    long countDueRetries(@Param("shipper") AppUser shipper, @Param("now") java.time.LocalDateTime now);

    @Query("""
            select o from Order o where o.shipper = :shipper
              and (:status is null or o.status = :status)
              and (:keyword is null
                   or lower(o.trackingCode) like lower(concat('%', :keyword, '%')) escape '!'
                   or lower(o.receiverName) like lower(concat('%', :keyword, '%')) escape '!'
                   or o.receiverPhone like concat('%', :keyword, '%') escape '!')
              and (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
            """)
    Page<Order> searchOrdersOfShipper(@Param("shipper") AppUser shipper,
            @Param("status") Order.OrderStatus status, @Param("keyword") String keyword,
            @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.shipper = :shipper")
    Optional<Order> findLockedByIdAndShipper(@Param("id") Long id, @Param("shipper") AppUser shipper);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.trackingCode = :code")
    Optional<Order> findLockedByTrackingCode(@Param("code") String code);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(@Param("id") Long id);

    Page<Order> findByStatus(Order.OrderStatus status, Pageable pageable);

    Optional<Order> findByTrackingCodeAndShipper(String trackingCode, AppUser shipper);

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

    long countByStatus(Order.OrderStatus status);

    /**
     * Dem don dang tren duong: da lay hang xong nhung chua giao toi noi.
     * Dung cho khoi so lieu o trang chu.
     */
    long countByStatusIn(List<Order.OrderStatus> statuses);
}
