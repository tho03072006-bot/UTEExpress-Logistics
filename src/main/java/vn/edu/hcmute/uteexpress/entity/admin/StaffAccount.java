package vn.edu.hcmute.uteexpress.entity.admin;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/** Hồ sơ cấp tài khoản nội bộ, tách khỏi entity người dùng chung. */
@Entity
@Table(name = "staff_account")
public class StaffAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired = true;

    @Column(name = "access_version", nullable = false)
    private long accessVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "issued_by", nullable = false, length = 50)
    private String issuedBy;

    @Version
    private long revision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public void setPasswordChangeRequired(boolean value) { this.passwordChangeRequired = value; }
    public long getAccessVersion() { return accessVersion; }
    public void revokeSessions() { accessVersion++; updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public long getRevision() { return revision; }
}
