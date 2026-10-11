package vn.edu.hcmute.uteexpress.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.dto.tracking.CodTotals;
import vn.edu.hcmute.uteexpress.entity.*;

public interface CodSettlementItemRepository extends JpaRepository<CodSettlementItem, Long> {
    /**
     * Cước COD Shipper đã thu nhưng chưa nộp: đơn giao thành công do chính Shipper ghi nhận trong nhật ký,
     * thanh toán COD đã PAID và chưa nằm trong phiếu nào (phiếu bị từ chối đã trả đơn lại).
     */
    String UNSETTLED = """
            where o.shipper = :shipper and o.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.DELIVERED
              and p.method = vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentMethod.COD
              and p.status = vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentStatus.PAID
              and exists (select h.id from OrderStatusHistory h where h.order = o and h.actor = :shipper
                and h.status = vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.DELIVERED)
              and not exists (select i.id from CodSettlementItem i where i.order = o)
            """;

    @Query("select p from OrderPayment p join fetch p.order o " + UNSETTLED + " order by p.paidAt, o.id")
    List<OrderPayment> unsettled(@Param("shipper") AppUser shipper, Pageable pageable);

    @Query("select p from OrderPayment p join fetch p.order o " + UNSETTLED + " and o.id in :ids order by p.paidAt, o.id")
    List<OrderPayment> unsettledIn(@Param("shipper") AppUser shipper, @Param("ids") Collection<Long> ids);

    @Query("select new vn.edu.hcmute.uteexpress.dto.tracking.CodTotals(count(p), sum(p.amount)) from OrderPayment p join p.order o " + UNSETTLED)
    CodTotals unsettledTotals(@Param("shipper") AppUser shipper);

    @Query("select i from CodSettlementItem i join fetch i.order where i.settlement = :settlement order by i.id")
    List<CodSettlementItem> findWithOrders(@Param("settlement") CodSettlement settlement);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from CodSettlementItem i where i.settlement = :settlement")
    int deleteBySettlement(@Param("settlement") CodSettlement settlement);
}
