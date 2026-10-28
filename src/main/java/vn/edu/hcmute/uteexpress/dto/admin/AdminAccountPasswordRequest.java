package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class AdminAccountPasswordRequest extends AdminAccountActionRequest {
    @Valid @NotNull(message = "Thiếu mật khẩu mới.")
    private StaffPasswordRequest credentials = new StaffPasswordRequest();
    public StaffPasswordRequest getCredentials() { return credentials; }
    public void setCredentials(StaffPasswordRequest value) { credentials = value; }
    @Override public void clearSecrets() {
        super.clearSecrets();
        if (credentials != null) { credentials.clearSecrets(); }
    }
}
