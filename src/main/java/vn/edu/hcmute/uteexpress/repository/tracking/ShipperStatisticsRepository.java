package vn.edu.hcmute.uteexpress.repository.tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;

public interface ShipperStatisticsRepository
        extends Repository<Order, Long> {

    @Query("""
            select o from Order o where o.shipper = :shipper and o.updatedAt >= :fromInclusive and o.updatedAt < :toExclusive
              and not exists (select h.id from OrderStatusHistory h where h.order = o and h.previousStatus <> h.status)
            """)
    List<Order> findByShipperAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            @Param("shipper") AppUser shipper, @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);

    interface DailyCod { java.time.LocalDate getDate(); BigDecimal getAmount(); }
    @Query("""
            select cast(p.paidAt as LocalDate) as date, sum(p.amount) as amount from OrderPayment p
            where p.order.shipper = :shipper and p.method = vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentMethod.COD
              and p.status = vn.edu.hcmute.uteexpress.entity.OrderPayment.PaymentStatus.PAID
              and p.paidAt >= :from and p.paidAt < :to group by cast(p.paidAt as LocalDate)
            """)
    List<DailyCod> dailyCod(@Param("shipper") AppUser shipper, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from OrderPayment payment
            where payment.order.shipper = :shipper
              and payment.method = :method
              and payment.status = :status
              and payment.paidAt >= :fromInclusive
              and payment.paidAt < :toExclusive
            """)
    BigDecimal sumCollectedCod(
            @Param("shipper") AppUser shipper,
            @Param("method") OrderPayment.PaymentMethod method,
            @Param("status") OrderPayment.PaymentStatus status,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);
}
