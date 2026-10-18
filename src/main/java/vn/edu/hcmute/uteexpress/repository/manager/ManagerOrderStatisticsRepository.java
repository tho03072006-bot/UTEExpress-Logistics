package vn.edu.hcmute.uteexpress.repository.manager;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.Order;

/**
 * Repository chi doc so lieu don hang cho phan Manager/Admin cua TV3.
 * Tach rieng de khong sua OrderRepository ma cac thanh vien khac dang su dung.
 */
public interface ManagerOrderStatisticsRepository extends Repository<Order, Long> {

    long count();

    long countByStatus(Order.OrderStatus status);

    @Query("select o.status as status, count(o.id) as total from Order o group by o.status")
    java.util.List<StatusCount> countGroupedStatuses();

    @Query("""
        select year(o.createdAt) as year, month(o.createdAt) as month, day(o.createdAt) as day,
               count(o.id) as total from Order o
        where o.createdAt >= :from and o.createdAt < :to
        group by year(o.createdAt), month(o.createdAt), day(o.createdAt)
        order by year(o.createdAt), month(o.createdAt), day(o.createdAt)
        """)
    java.util.List<DayCount> countCreatedByDay(@Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    interface StatusCount { Order.OrderStatus getStatus(); long getTotal(); }
    interface DayCount { int getYear(); int getMonth(); int getDay(); long getTotal(); }
}
