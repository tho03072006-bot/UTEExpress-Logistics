package vn.edu.hcmute.uteexpress.entity.admin;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Chính sách vận hành do Admin cấu hình; migration tạo duy nhất bản ghi ID 1. */
@Entity @Table(name = "operations_policy")
public class OperationsPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable = false) private long revision;
    @Column(nullable = false) private int pickupHours = 24;
    @Column(nullable = false) private int standardHours = 120;
    @Column(nullable = false) private int expressHours = 48;
    @Column(nullable = false) private int superExpressHours = 4;
    @Column(nullable = false) private int maxDeliveryAttempts = 3;
    @Column(nullable = false) private int complaintResponseHours = 24;
    @Column(nullable = false) private int complaintResolveHours = 168;
    @Column(nullable = false, length = 50) private String updatedBy = "database-operator";
    @Column(nullable = false) private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public long getRevision() { return revision; }
    public int getPickupHours() { return pickupHours; }
    public int getStandardHours() { return standardHours; }
    public int getExpressHours() { return expressHours; }
    public int getSuperExpressHours() { return superExpressHours; }
    public int getMaxDeliveryAttempts() { return maxDeliveryAttempts; }
    public int getComplaintResponseHours() { return complaintResponseHours; }
    public int getComplaintResolveHours() { return complaintResolveHours; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void configure(int pickup, int standard, int express, int superExpress, int attempts,
            int response, int resolve, String actor, LocalDateTime now) {
        pickupHours = pickup; standardHours = standard; expressHours = express;
        superExpressHours = superExpress; maxDeliveryAttempts = attempts;
        complaintResponseHours = response; complaintResolveHours = resolve; updatedBy = actor; updatedAt = now;
    }
}
