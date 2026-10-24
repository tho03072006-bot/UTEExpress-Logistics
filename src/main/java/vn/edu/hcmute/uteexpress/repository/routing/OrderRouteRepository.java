package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.OrderRoute;

/** Truy vấn dữ liệu OrderRoute; quyền và nghiệp vụ được kiểm tra tại tầng service.
 *  Đơn khách tự mang ra chi nhánh (DROP_OFF) không cần shipper lấy nên không thuộc hàng chờ phân công chặng PICKUP. */
public interface OrderRouteRepository extends JpaRepository<OrderRoute, Long> {
    /**
     * Điều kiện JPQL dùng chung cho đơn có bí danh {@code o} và tham số {@code :branchId} (null = mọi chi nhánh):
     * đơn đã có tuyến chỉ thuộc chi nhánh gửi hoặc nhận; đơn cũ chưa có tuyến hiện cho mọi chi nhánh tới khi chuyển dữ liệu.
     */
    String BRANCH_SCOPE = " (:branchId is null or not exists (select r0.id from OrderRoute r0 where r0.order = o)"
            + " or exists (select r1.id from OrderRoute r1 where r1.order = o and (r1.originBranch.id = :branchId or r1.destBranch.id = :branchId))) ";
    String DISPATCHABLE = "((r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.PICKUP and r.order.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP and r.pickupMode = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.COURIER and (:branchId is null or r.originBranch.id = :branchId)) or (r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.DEST_HUB and r.order.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP and (:branchId is null or r.destBranch.id = :branchId)))";
    @org.springframework.data.jpa.repository.Query("select r.order from OrderRoute r where " + DISPATCHABLE + " order by r.order.createdAt, r.order.id")
    org.springframework.data.domain.Page<vn.edu.hcmute.uteexpress.entity.Order> pending(Long branchId, org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Query("select r.order.trackingCode from OrderRoute r where r.order.shipper is null and " + DISPATCHABLE + " order by r.order.createdAt, r.order.id")
    List<String> unassignedCodes(Long branchId);
    Optional<OrderRoute> findByOrder_Id(Long orderId);
    /** Đơn đang chạy shipper giữ mà chặng hiện tại thuộc chi nhánh khác (chờ lấy: chi nhánh gửi; còn lại: chi nhánh nhận). */
    @org.springframework.data.jpa.repository.Query("""
        select count(r) from OrderRoute r where r.order.shipper.id = :shipperId and r.order.status in :statuses
          and ((r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.PICKUP and r.originBranch.id <> :branchId)
            or (r.stage <> vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.PICKUP and r.destBranch.id <> :branchId))
        """)
    long countHeldOutsideBranch(@org.springframework.data.repository.query.Param("shipperId") Long shipperId,
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<vn.edu.hcmute.uteexpress.entity.Order.OrderStatus> statuses,
            @org.springframework.data.repository.query.Param("branchId") Long branchId);
    /** Tuyến kèm khu vực lấy/giao của nhiều đơn một lần (sắp lộ trình shipper, không truy vấn từng đơn). */
    @org.springframework.data.jpa.repository.Query("select r from OrderRoute r join fetch r.pickupArea join fetch r.deliveryArea where r.order.id in :ids")
    List<OrderRoute> findWithAreasByOrderIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
    /** ID các đơn trong danh sách thuộc phạm vi chi nhánh (xem BRANCH_SCOPE). */
    @org.springframework.data.jpa.repository.Query("select o.id from Order o where o.id in :ids and" + BRANCH_SCOPE)
    List<Long> idsInScope(java.util.Collection<Long> ids, Long branchId);
    /** Xem được ở hai đầu tuyến, nhưng thao tác giao lại/hoàn chỉ tại kho đang phụ trách chặng giao. */
    @org.springframework.data.jpa.repository.Query("select o.id from Order o where o.id in :ids and " + BRANCH_SCOPE + """
        and (not exists (select old.id from OrderRoute old where old.order = o)
          or exists (select delivery.id from OrderRoute delivery where delivery.order = o and delivery.returnLeg = false
            and delivery.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.LAST_MILE
            and (:branchId is null or delivery.destBranch.id = :branchId)))
        """)
    List<Long> idsAtDeliveryHub(java.util.Collection<Long> ids, Long branchId);
    /** Đơn đã nhập kho gửi, chưa được xếp lên chuyến nào đang xếp hàng. */
    @org.springframework.data.jpa.repository.Query("select r from OrderRoute r join fetch r.order o join fetch r.destBranch"
            + " where r.originBranch.id = :branchId and r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.ORIGIN_HUB and r.returnLeg = false"
            + " and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP"
            + " and not exists (select i.id from LinehaulItem i where i.order = o and i.trip.status = vn.edu.hcmute.uteexpress.entity.routing.LinehaulTrip.Status.LOADING)"
            + " order by o.createdAt, o.id")
    List<OrderRoute> waitingAtOriginHub(Long branchId);
    /** B9: hàng hoàn đang ở kho nhận (chi nhánh này), chưa xếp lên chuyến về chi nhánh gửi. */
    @org.springframework.data.jpa.repository.Query("select r from OrderRoute r join fetch r.order o join fetch r.originBranch"
            + " where r.destBranch.id = :branchId and r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.DEST_HUB and r.returnLeg = true"
            + " and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED"
            + " and not exists (select i.id from LinehaulItem i where i.order = o and i.trip.status = vn.edu.hcmute.uteexpress.entity.routing.LinehaulTrip.Status.LOADING)"
            + " order by o.updatedAt, o.id")
    List<OrderRoute> waitingReturnAtDestHub(Long branchId);
    /** B9: hàng hoàn đã về kho gửi (chi nhánh này), chờ ghi nhận trả người gửi. */
    @org.springframework.data.jpa.repository.Query("select r from OrderRoute r join fetch r.order o join fetch r.destBranch"
            + " where r.originBranch.id = :branchId and r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.ORIGIN_HUB and r.returnLeg = true"
            + " and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.FAILED"
            + " order by o.updatedAt, o.id")
    List<OrderRoute> returnedAtOriginHub(Long branchId);
    long countByDestBranch_IdAndStage(Long branchId, OrderRoute.Stage stage);
    /** Đơn tới kho nhận chờ phân công giao (không tính hàng hoàn đang chờ chuyến về). */
    @org.springframework.data.jpa.repository.Query("select count(r) from OrderRoute r where r.destBranch.id = :branchId"
            + " and r.stage = :stage and r.returnLeg = false and r.order.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP")
    long countByDestBranch_IdAndStageAndReturnLegFalse(Long branchId, OrderRoute.Stage stage);
    /** Đơn khách đã chọn tự mang tới quầy chi nhánh gửi nhưng quầy chưa nhận. */
    @org.springframework.data.jpa.repository.Query("select r from OrderRoute r join fetch r.order o join fetch r.destBranch"
            + " where r.originBranch.id = :branchId and r.stage = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.PICKUP"
            + " and r.pickupMode = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.DROP_OFF"
            + " and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP order by o.createdAt, o.id")
    List<OrderRoute> waitingDropOff(Long branchId);
    /** ID các đơn khách tự mang ra chi nhánh trong danh sách, để trang cảnh báo ghi đúng nhãn. */
    @org.springframework.data.jpa.repository.Query("select r.order.id from OrderRoute r where r.pickupMode = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.DROP_OFF"
            + " and r.order.id in :ids")
    List<Long> dropOffOrderIds(java.util.Collection<Long> ids);
}
