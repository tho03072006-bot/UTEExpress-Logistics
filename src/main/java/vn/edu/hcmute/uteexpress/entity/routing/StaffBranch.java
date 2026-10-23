package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/** Mỗi nhân sự chỉ có một chi nhánh; kiểm tra vai trò và đơn đang giữ tại service. */
@Entity
@Table(name = "staff_branch", uniqueConstraints = @UniqueConstraint(name = "uk_staff_branch_user", columnNames = "user_id"),
        indexes = @Index(name = "idx_staff_branch", columnList = "branch_id"))
public class StaffBranch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_staff_branch_user")) private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_staff_branch_branch")) private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false, foreignKey = @ForeignKey(name = "fk_staff_branch_actor")) private AppUser assignedBy;
    @Column(nullable = false) private LocalDateTime assignedAt;
    protected StaffBranch() { }
    public StaffBranch(AppUser user, Branch branch, AppUser actor, LocalDateTime now) {
        this.user = user; assign(branch, actor, now);
    }
    public void assign(Branch branch, AppUser actor, LocalDateTime now) { this.branch = branch; assignedBy = actor; assignedAt = now; }
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Branch getBranch() { return branch; }
    public AppUser getAssignedBy() { return assignedBy; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
}
