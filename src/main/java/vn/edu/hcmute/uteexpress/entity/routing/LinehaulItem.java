package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import vn.edu.hcmute.uteexpress.entity.Order;

/** Một kiện của chuyến; giữ lịch sử để đơn có thể đi chuyến hoàn ngược sau này. */
@Entity
@Table(name = "linehaul_item", uniqueConstraints = @UniqueConstraint(name = "uk_linehaul_item", columnNames = {"trip_id", "order_id"}),
        indexes = @Index(name = "idx_linehaul_item_order", columnList = "order_id"))
public class LinehaulItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, foreignKey = @ForeignKey(name = "fk_item_trip")) private LinehaulTrip trip;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_item_order")) private Order order;
    @Column(nullable = false) private boolean received;
    protected LinehaulItem() { }
    public LinehaulItem(LinehaulTrip trip, Order order) { this.trip = trip; this.order = order; }
    public Long getId() { return id; }
    public LinehaulTrip getTrip() { return trip; }
    public Order getOrder() { return order; }
    public boolean isReceived() { return received; }
    public void markReceived() {
        if (received) throw new IllegalStateException("Kiện đã được nhận vào kho.");
        received = true;
    }
}
