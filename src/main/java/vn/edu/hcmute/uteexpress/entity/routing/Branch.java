package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

/** Chi nhánh và kho; điểm xuất phát là tâm khu vực giao nhận nội bộ của kho. */
@Entity
@Table(name = "branch", uniqueConstraints = @UniqueConstraint(name = "uk_branch_code", columnNames = "code"))
public class Branch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Nationalized @Column(nullable = false, length = 10) private String code;
    @Nationalized @Column(nullable = false, length = 100) private String name;
    @Nationalized @Column(nullable = false, length = 10) private String provinceCode;
    @Nationalized @Column(nullable = false, length = 255) private String hubAddress;
    // Lưu mã để tránh vòng khóa ngoại branch -> province_area -> branch khi tạo chi nhánh.
    // Service phải kiểm tra khu vực kho thuộc chi nhánh; dữ liệu seed được kiểm tra tương tự.
    @Nationalized @Column(nullable = false, length = 40) private String hubAreaCode;
    @Column(nullable = false) private boolean active = true;
    @Version @Column(nullable = false) private long revision;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected Branch() { }
    public Branch(String code, String name, String provinceCode, String hubAddress, String hubAreaCode, LocalDateTime now) {
        this.code = code; this.name = name; this.provinceCode = provinceCode;
        this.hubAddress = hubAddress; this.hubAreaCode = hubAreaCode; createdAt = now; updatedAt = now;
    }
    public void configure(String name, String hubAddress, String hubAreaCode, boolean active, LocalDateTime now) {
        this.name = name; this.hubAddress = hubAddress; this.hubAreaCode = hubAreaCode;
        this.active = active; updatedAt = now;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getProvinceCode() { return provinceCode; }
    public String getHubAddress() { return hubAddress; }
    public String getHubAreaCode() { return hubAreaCode; }
    public boolean isActive() { return active; }
    public long getRevision() { return revision; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
