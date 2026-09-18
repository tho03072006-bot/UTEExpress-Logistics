package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;

public class StaffAccountUpdateRequest {
    @NotNull(message = "Thiếu phiên bản tài khoản.")
    @PositiveOrZero(message = "Phiên bản tài khoản không hợp lệ.")
    private Long revision;
    @NotNull(message = "Vui lòng chọn cấp quyền.")
    private AppUser.Role role;
    @NotNull(message = "Vui lòng chọn trạng thái.")
    private Boolean active;
    @AssertTrue(message = "Cấp quyền phải là MANAGER hoặc ADMIN.")
    public boolean isPrivilegedRole() {
        return role == null || role == AppUser.Role.MANAGER || role == AppUser.Role.ADMIN;
    }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public AppUser.Role getRole() { return role; }
    public void setRole(AppUser.Role role) { this.role = role; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}

