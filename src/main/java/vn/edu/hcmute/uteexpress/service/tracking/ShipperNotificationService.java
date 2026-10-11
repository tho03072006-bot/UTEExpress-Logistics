package vn.edu.hcmute.uteexpress.service.tracking;

import java.time.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.ShipperNotification;
import vn.edu.hcmute.uteexpress.repository.ShipperNotificationRepository;

@Service
@Transactional(readOnly = true)
public class ShipperNotificationService {
    private final ShipperNotificationRepository notifications;
    private final LogisticsAccess access;
    private final Clock clock;
    public ShipperNotificationService(ShipperNotificationRepository notifications, LogisticsAccess access, Clock clock) {
        this.notifications = notifications; this.access = access; this.clock = clock;
    }
    public Page<ShipperNotification> list(String username, int page) {
        return notifications.findByRecipient(access.shipper(username), PageRequest.of(LogisticsInput.page(page), 10,
                Sort.by("createdAt", "id").descending()));
    }
    public long unread(String username) { return notifications.countByRecipientAndReadAtIsNull(access.shipper(username)); }
    @Transactional
    public void read(Long id, String username) {
        var notification = notifications.findByIdAndRecipient(id, access.shipper(username))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo của bạn."));
        notification.markRead(LocalDateTime.now(clock));
        notifications.save(notification);
    }
    @Transactional
    public void readAll(String username) { notifications.markAllRead(access.shipper(username), LocalDateTime.now(clock)); }
}
