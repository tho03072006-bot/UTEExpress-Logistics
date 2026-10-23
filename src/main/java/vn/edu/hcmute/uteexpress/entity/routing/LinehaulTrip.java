package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Nationalized;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/** Chuyến xe giữa hai kho; tài xế là thông tin chuyến, không phải tài khoản hay vai trò mới. */
@Entity
@Table(name = "linehaul_trip", uniqueConstraints = @UniqueConstraint(name = "uk_linehaul_code", columnNames = "code"),
        indexes = {@Index(name = "idx_trip_from_status", columnList = "from_branch_id,status"),
                   @Index(name = "idx_trip_to_status", columnList = "to_branch_id,status")})
@Check(constraints = "from_branch_id <> to_branch_id and item_count >= 0")
public class LinehaulTrip {
    public enum Status { LOADING, DEPARTED, ARRIVED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Nationalized @Column(nullable = false, length = 40) private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_from")) private Branch fromBranch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_to")) private Branch toBranch;
    @Nationalized @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.LOADING;
    @Nationalized @Column(nullable = false, length = 20) private String vehiclePlate;
    @Nationalized @Column(nullable = false, length = 100) private String driverName;
    @Nationalized @Column(nullable = false, length = 20) private String driverPhone;
    @Nationalized @Column(length = 50) private String sealCode;
    @Column(nullable = false) private int itemCount;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departed_by", foreignKey = @ForeignKey(name = "fk_trip_departed_by")) private AppUser departedBy;
    private LocalDateTime departedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrived_by", foreignKey = @ForeignKey(name = "fk_trip_arrived_by")) private AppUser arrivedBy;
    private LocalDateTime arrivedAt;
    @Nationalized @Column(length = 500) private String arrivalNote;
    @Version @Column(nullable = false) private long revision;
    protected LinehaulTrip() { }
    public LinehaulTrip(String code, Branch fromBranch, Branch toBranch, String vehiclePlate, String driverName, String driverPhone) {
        this.code = code; this.fromBranch = fromBranch; this.toBranch = toBranch;
        this.vehiclePlate = vehiclePlate; this.driverName = driverName; this.driverPhone = driverPhone;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public Branch getFromBranch() { return fromBranch; }
    public Branch getToBranch() { return toBranch; }
    public Status getStatus() { return status; }
    public String getVehiclePlate() { return vehiclePlate; }
    public String getDriverName() { return driverName; }
    public String getDriverPhone() { return driverPhone; }
    public String getSealCode() { return sealCode; }
    public int getItemCount() { return itemCount; }
    public AppUser getDepartedBy() { return departedBy; }
    public LocalDateTime getDepartedAt() { return departedAt; }
    public AppUser getArrivedBy() { return arrivedBy; }
    public LocalDateTime getArrivedAt() { return arrivedAt; }
    public String getArrivalNote() { return arrivalNote; }
    public long getRevision() { return revision; }
    /** Niêm phong và xuất kho gửi; số kiện chốt tại thời điểm xuất. */
    public void depart(String sealCode, int itemCount, AppUser by, LocalDateTime at) {
        if (status != Status.LOADING) throw new IllegalStateException("Chuyến đã xuất kho hoặc đã tới nơi.");
        if (itemCount <= 0) throw new IllegalArgumentException("Chuyến chưa có kiện nào để xuất kho.");
        this.sealCode = sealCode; this.itemCount = itemCount; departedBy = by; departedAt = at; status = Status.DEPARTED;
    }
    /** Kho nhận xác nhận chuyến đã tới; ghi chú nêu niêm phong lệch hoặc kiện thiếu nếu có. */
    public void arrive(AppUser by, LocalDateTime at, String note) {
        if (status != Status.DEPARTED) throw new IllegalStateException("Chuyến chưa xuất kho hoặc đã được nhận.");
        arrivedBy = by; arrivedAt = at; arrivalNote = note; status = Status.ARRIVED;
    }
    /** Cập nhật số kiện khi còn đang xếp hàng (thêm hoặc bớt đơn). */
    public void recount(int itemCount) {
        if (status != Status.LOADING) throw new IllegalStateException("Chỉ đổi danh sách kiện khi chuyến đang xếp hàng.");
        this.itemCount = itemCount;
    }
}
