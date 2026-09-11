package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByTrackingCode(String trackingCode);

    List<Order> findBySender(AppUser sender);

    List<Order> findByShipper(AppUser shipper);

    List<Order> findByStatus(Order.OrderStatus status);
}
