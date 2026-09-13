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

    List<Order> findByShipperAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            AppUser shipper,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive);

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
