package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class AdminAccountUpdateRequest extends AdminAccountActionRequest {
    @Valid @NotNull(message = "Thiếu thông tin tài khoản.")
    private AdminAccountProfileRequest profile = new AdminAccountProfileRequest();
    @NotNull(message = "Vui lòng chọn trạng thái.")
    private Boolean active;
    public AdminAccountProfileRequest getProfile() { return profile; }
    public void setProfile(AdminAccountProfileRequest value) { profile = value; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean value) { active = value; }
}
