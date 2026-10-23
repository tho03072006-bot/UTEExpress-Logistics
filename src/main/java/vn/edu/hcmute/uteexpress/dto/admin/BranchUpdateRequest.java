package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import vn.edu.hcmute.uteexpress.entity.routing.Branch;

/** Mã chi nhánh và tỉnh cố định; chỉ cập nhật thông tin kho và nhận đơn mới. */
public class BranchUpdateRequest {
    @NotNull @PositiveOrZero private Long revision;
    @NotBlank(message = "Nhập tên chi nhánh.") @Size(min = 2, max = 100)
    @Pattern(regexp="(?=.*\\p{L})[^\\p{Cntrl}]+", message="Tên chi nhánh phải có chữ cái và không chứa ký tự điều khiển.") private String name;
    @NotBlank(message = "Nhập địa chỉ kho.") @Size(min = 5, max = 255)
    @Pattern(regexp=vn.edu.hcmute.uteexpress.validation.ShippingInputValidation.ADDRESS, message="Địa chỉ kho phải có chữ hoặc số và không chứa ký tự điều khiển.") private String hubAddress;
    @NotBlank(message = "Chọn khu vực kho.") @Size(max = 40) private String hubAreaCode;
    @NotNull(message = "Chọn trạng thái nhận đơn.") private Boolean active;
    @NotBlank(message = "Nhập lý do thay đổi.") @Size(min = 5, max = 300)
    @Pattern(regexp="[^\\p{Cntrl}]+", message="Lý do không chứa ký tự điều khiển.") private String reason;
    public static BranchUpdateRequest from(Branch branch) {
        var form = new BranchUpdateRequest(); form.revision = branch.getRevision(); form.name = branch.getName();
        form.hubAddress = branch.getHubAddress(); form.hubAreaCode = branch.getHubAreaCode(); form.active = branch.isActive();
        return form;
    }
    public Long getRevision() { return revision; }
    public void setRevision(Long value) { revision = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value == null ? null : value.strip(); }
    public String getHubAddress() { return hubAddress; }
    public void setHubAddress(String value) { hubAddress = value == null ? null : value.strip(); }
    public String getHubAreaCode() { return hubAreaCode; }
    public void setHubAreaCode(String value) { hubAreaCode = value == null ? null : value.strip(); }
    public Boolean getActive() { return active; }
    public void setActive(Boolean value) { active = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value == null ? null : value.strip(); }
}
