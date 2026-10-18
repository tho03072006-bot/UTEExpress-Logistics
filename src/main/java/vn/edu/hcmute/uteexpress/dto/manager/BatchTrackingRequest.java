package vn.edu.hcmute.uteexpress.dto.manager;

import jakarta.validation.constraints.*;

public class BatchTrackingRequest {
    @NotBlank(message = "Nhập ít nhất một mã vận đơn.")
    @Size(max = 500, message = "Danh sách vận đơn tối đa 500 ký tự.")
    private String codes;
    public String getCodes() { return codes; }
    public void setCodes(String codes) { this.codes = codes; }
}
