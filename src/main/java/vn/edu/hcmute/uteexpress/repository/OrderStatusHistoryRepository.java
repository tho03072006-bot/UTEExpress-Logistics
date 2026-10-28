package vn.edu.hcmute.uteexpress.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderStatusHistory;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    /**
     * Lượt chuyển trạng thái tính cho thống kê của Shipper: do chính Shipper thực hiện, cộng với thao tác của
     * Manager/Admin trên đơn đang giao cho Shipper đó (ví dụ Manager hoàn tất chuyển hoàn). Nhật ký vẫn giữ
     * đúng người thao tác; đơn đã chuyển cho Shipper khác thì lượt của Shipper cũ không tính sang người mới.
     */
    @org.springframework.data.jpa.repository.Query("""
            select new vn.edu.hcmute.uteexpress.dto.tracking.ShipperActivity(h.order.id, h.status, h.occurredAt)
            from OrderStatusHistory h where h.previousStatus <> h.status
              and (h.actor = :actor or (h.order.shipper = :actor
                and h.actor.role in (vn.edu.hcmute.uteexpress.entity.AppUser.Role.MANAGER, vn.edu.hcmute.uteexpress.entity.AppUser.Role.ADMIN)))
              and h.occurredAt >= :from and h.occurredAt < :to order by h.occurredAt, h.id
            """)
    java.util.List<vn.edu.hcmute.uteexpress.dto.tracking.ShipperActivity> activities(
            @org.springframework.data.repository.query.Param("actor") vn.edu.hcmute.uteexpress.entity.AppUser actor,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);
    /** Lấy hàng thất bại gần nhất của từng đơn (giữ chờ lấy hàng, có lý do). */
    @org.springframework.data.jpa.repository.Query("""
            select h from OrderStatusHistory h where h.order.id in :ids
              and h.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
              and h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and h.failureReason is not null
              and h.id = (select max(h2.id) from OrderStatusHistory h2 where h2.order = h.order
                and h2.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                and h2.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and h2.failureReason is not null)
            """)
    java.util.List<OrderStatusHistory> latestPickupFailures(@org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);

    /**
     * Mọi lần giao thất bại và lấy hàng thất bại của các đơn, cũ trước mới sau. Một truy vấn đủ để tính
     * lần thất bại gần nhất và số lần thất bại của từng đơn (trang danh sách và trang chi tiết).
     */
    @org.springframework.data.jpa.repository.Query("""
            select h from OrderStatusHistory h where h.order.id in :ids
              and ((h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED and h.previousStatus <> h.status)
                or (h.failureReason is not null
                  and h.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
                  and h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP))
            order by h.id
            """)
    java.util.List<OrderStatusHistory> failureEntries(@org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);
    /** Lúc đơn rời "Chờ lấy hàng" lần đầu (Shipper lấy hàng hoặc kho nhận tại quầy), mỗi dòng là [id đơn, thời điểm]. */
    @org.springframework.data.jpa.repository.Query("""
            select h.order.id, min(h.occurredAt) from OrderStatusHistory h where h.order.id in :ids
              and h.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
              and h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP
            group by h.order.id
            """)
    java.util.List<Object[]> firstPickupTimes(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    @org.springframework.data.jpa.repository.Query("""
            select count(h.id) from OrderStatusHistory h where h.order = :order and h.failureReason is not null
              and h.previousStatus = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
              and h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP
            """)
    long countPickupFailures(@org.springframework.data.repository.query.Param("order") Order order);

    /** Số việc Shipper đã hoàn thành từ một thời điểm: lấy hàng xong, giao thành công, hoàn trả. */
    @org.springframework.data.jpa.repository.Query("""
            select count(h.id) from OrderStatusHistory h where h.actor = :actor and h.previousStatus <> h.status
              and h.status in (vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP,
                vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.DELIVERED, vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.RETURNED)
              and h.occurredAt >= :from
            """)
    long countDoneSince(@org.springframework.data.repository.query.Param("actor") vn.edu.hcmute.uteexpress.entity.AppUser actor,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    Page<OrderStatusHistory> findByOrder(Order order, Pageable pageable);
    /** Chỉ đếm lần chuyển vào trạng thái; nhật ký di chuyển/ghi chú giữ nguyên trạng thái không phải một lượt giao. */
    @org.springframework.data.jpa.repository.Query("select count(h) from OrderStatusHistory h where h.order = :order and h.status = :status and h.previousStatus <> h.status")
    long countByOrderAndStatus(Order order, Order.OrderStatus status);
    /** Số mục lịch sử theo trạng thái của nhiều đơn: cặp [mã đơn, số mục]; đơn không có mục nào thì không có dòng. */
    @org.springframework.data.jpa.repository.Query("select h.order.id, count(h) from OrderStatusHistory h where h.order.id in :ids and h.status = :status group by h.order.id")
    List<Object[]> countByOrderIdsAndStatus(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids,
            @org.springframework.data.repository.query.Param("status") Order.OrderStatus status);
    /** Các lần chuyển sang một trạng thái (khác trạng thái trước) của nhiều đơn, mới nhất trước; bản ghi đầu của mỗi đơn là lần gần nhất. */
    @org.springframework.data.jpa.repository.Query("select h from OrderStatusHistory h where h.order.id in :ids and h.status = :status and h.previousStatus <> :status order by h.occurredAt desc, h.id desc")
    List<OrderStatusHistory> transitionsInto(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids,
            @org.springframework.data.repository.query.Param("status") Order.OrderStatus status);
    Optional<OrderStatusHistory> findFirstByOrderAndStatusAndPreviousStatusNotOrderByOccurredAtDescIdDesc(
            Order order, Order.OrderStatus status, Order.OrderStatus previousStatus);
    Optional<OrderStatusHistory> findFirstByOrderAndStatusOrderByOccurredAtDescIdDesc(Order order, Order.OrderStatus status);
    Optional<OrderStatusHistory> findFirstByOrderAndPreviousStatusAndStatusOrderByOccurredAtDescIdDesc(
            Order order, Order.OrderStatus previousStatus, Order.OrderStatus status);
}
