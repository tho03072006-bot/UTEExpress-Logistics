package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "shipper_notification", uniqueConstraints =
        @UniqueConstraint(name = "uk_notification_order_recipient", columnNames = {"order_id", "recipient_id"}),
        indexes = @Index(name = "idx_notification_recipient_read", columnList = "recipient_id,read_at,created_at"))
public class ShipperNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;
    @Nationalized @Column(nullable = false, length = 200)
    private String message;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "read_at")
    private LocalDateTime readAt;

    public ShipperNotification() { }
    public void assign(Order order, AppUser recipient, LocalDateTime now) {
        this.order = order;
        this.recipient = recipient;
        this.message = "Bạn được phân công vận đơn " + order.getTrackingCode() + ".";
        this.createdAt = now;
        this.readAt = null;
    }
    public void markRead(LocalDateTime now) { if (readAt == null) readAt = now; }
    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public AppUser getRecipient() { return recipient; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public boolean isAssigned() {
        return order.getShipper() != null && recipient.getId().equals(order.getShipper().getId());
    }
}
