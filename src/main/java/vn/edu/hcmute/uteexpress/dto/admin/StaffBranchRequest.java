package vn.edu.hcmute.uteexpress.dto.admin;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.*;

/** Khu vực giao nhận nội bộ; không dùng địa chỉ nhà của nhân sự để điều phối. */
public class StaffBranchRequest {
    @Positive(message = "Chi nhánh không hợp lệ.") private Long branchId;
    @Size(max = 40, message = "Mã khu vực quá dài.") private String primaryAreaCode;
    @Size(max = 59, message = "Danh sách khu vực quá dài.")
    private List<@NotBlank(message = "Thiếu mã khu vực.") @Size(max = 40) String> secondaryAreaCodes = new ArrayList<>();
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long value) { branchId = value; }
    public String getPrimaryAreaCode() { return primaryAreaCode; }
    public void setPrimaryAreaCode(String value) { primaryAreaCode = value == null ? null : value.strip(); }
    public List<String> getSecondaryAreaCodes() { return secondaryAreaCodes; }
    public void setSecondaryAreaCodes(List<String> value) { secondaryAreaCodes = value == null ? new ArrayList<>() : new ArrayList<>(value); }
}
