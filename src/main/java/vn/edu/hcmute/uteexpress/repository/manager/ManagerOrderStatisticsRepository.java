package vn.edu.hcmute.uteexpress.repository.manager;

import org.springframework.data.repository.Repository;
import vn.edu.hcmute.uteexpress.entity.Order;

/**
 * Repository chi doc so lieu don hang cho phan Manager/Admin cua TV3.
 * Tach rieng de khong sua OrderRepository ma cac thanh vien khac dang su dung.
 */
public interface ManagerOrderStatisticsRepository extends Repository<Order, Long> {

    long count();

    long countByStatus(Order.OrderStatus status);
}
