package vn.edu.hcmute.uteexpress.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.*;

public interface ShipperNotificationRepository extends JpaRepository<ShipperNotification, Long> {
    Page<ShipperNotification> findByRecipient(AppUser recipient, Pageable pageable);
    Optional<ShipperNotification> findByOrderAndRecipient(Order order, AppUser recipient);
    Optional<ShipperNotification> findByIdAndRecipient(Long id, AppUser recipient);
    long countByRecipientAndReadAtIsNull(AppUser recipient);
    @Modifying
    @Query("update ShipperNotification n set n.readAt = :now where n.recipient = :recipient and n.readAt is null")
    int markAllRead(@Param("recipient") AppUser recipient, @Param("now") LocalDateTime now);
    @Modifying
    @Query("update ShipperNotification n set n.readAt = :now where n.order = :order and n.readAt is null")
    int dismissPreviousAssignment(@Param("order") Order order, @Param("now") LocalDateTime now);
}
