package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;

/** Nhật ký chỉ thêm mới; không thay đổi cấu trúc vận đơn dùng chung. */
@Entity
@Table(name = "order_status_history", indexes =
        @Index(name = "idx_journey_order_time", columnList = "order_id,occurred_at,id"))
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private AppUser actor;
    @Nationalized @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", nullable = false, length = 20)
    private Order.OrderStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Order.OrderStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "failure_reason", length = 30)
    private DeliveryFailureReason failureReason;
    @Nationalized @Column(length = 500)
    private String note;
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected OrderStatusHistory() { }
    public OrderStatusHistory(Order order, AppUser actor, Order.OrderStatus previousStatus,
            Order.OrderStatus status, DeliveryFailureReason failureReason, String note,
            LocalDateTime nextAttemptAt, LocalDateTime occurredAt) {
        this.order = order;
        this.actor = actor;
        this.actorName = actor.getFullName() == null || actor.getFullName().isBlank()
                ? actor.getUsername() : actor.getFullName();
        this.previousStatus = previousStatus;
        this.status = status;
        this.failureReason = failureReason;
        this.note = note;
        this.nextAttemptAt = nextAttemptAt;
        this.occurredAt = occurredAt;
    }
    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public AppUser getActor() { return actor; }
    public String getActorName() { return actorName; }
    public Order.OrderStatus getPreviousStatus() { return previousStatus; }
    public Order.OrderStatus getStatus() { return status; }
    public DeliveryFailureReason getFailureReason() { return failureReason; }
    public String getNote() { return note; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
