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

    /** Các bản theo chi nhánh (xem OrderRouteRepository.BRANCH_SCOPE) cho tổng quan của Manager. */
    @Query("select count(o.id) from Order o where" + vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository.BRANCH_SCOPE)
    long countInScope(@Param("branchId") Long branchId);
    @Query("select count(o.id) from Order o where o.status = :status and" + vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository.BRANCH_SCOPE)
    long countByStatusInScope(@Param("status") Order.OrderStatus status, @Param("branchId") Long branchId);
    @Query("select o.status as status, count(o.id) as total from Order o where" + vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository.BRANCH_SCOPE + " group by o.status")
    java.util.List<StatusCount> countGroupedStatuses(@Param("branchId") Long branchId);
    @Query("""
        select year(o.createdAt) as year, month(o.createdAt) as month, day(o.createdAt) as day,
               count(o.id) as total from Order o
        where o.createdAt >= :from and o.createdAt < :to and
        """ + vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository.BRANCH_SCOPE + """
        group by year(o.createdAt), month(o.createdAt), day(o.createdAt)
        order by year(o.createdAt), month(o.createdAt), day(o.createdAt)
        """)
    java.util.List<DayCount> countCreatedByDay(@Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to, @Param("branchId") Long branchId);

    interface StatusCount { Order.OrderStatus getStatus(); long getTotal(); }
    interface DayCount { int getYear(); int getMonth(); int getDay(); long getTotal(); }
}
