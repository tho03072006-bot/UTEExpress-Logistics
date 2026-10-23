package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;

/**
 * Dữ liệu người dùng nhập từ form thêm/sửa địa chỉ đã lưu.
 * Không bind thẳng vào Entity SavedAddress để tránh người dùng sửa lén field user/id.
 */
public class SavedAddressRequest {
    @NotBlank(message = "Chọn tỉnh/thành.") @Size(max = 10) private String provinceCode;
    @NotBlank(message = "Chọn khu vực giao nhận.") @Size(max = 40) private String areaCode;
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String value) { provinceCode = value; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String value) { areaCode = value; }

    @NotBlank(message = "Vui lòng nhập nhãn gợi nhớ cho địa chỉ")
    @Size(max = 50, message = "Nhãn địa chỉ tối đa 50 ký tự")
    @Pattern(regexp="[^\\p{Cntrl}]+",message="Nhãn không chứa ký tự điều khiển.")
    private String label;

    @NotBlank(message = "Vui lòng nhập họ tên người liên hệ")
    @Size(min=2,max = 100, message = "Họ tên từ 2–100 ký tự")
    @Pattern(regexp=vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.NAME,message="Tên liên hệ chỉ chứa chữ và dấu phân cách tên hợp lệ.")
    private String contactName;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp=vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.PHONE,message="Nhập số di động Việt Nam 10 số, đầu 03/05/07/08/09 hoặc dạng +84.")
    private String contactPhone;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết")
    @Size(min=5,max = 255, message = "Địa chỉ từ 5–255 ký tự")
    @Pattern(regexp="[^\\p{Cntrl}]+",message="Địa chỉ không chứa ký tự điều khiển.")
    private String addressLine;

    @NotNull(message = "Vui lòng chọn loại địa chỉ")
    private SavedAddress.AddressType addressType = SavedAddress.AddressType.SENDER;

    /** Checkbox "Đặt làm địa chỉ mặc định" trên form. */
    private boolean defaultAddress;

    public SavedAddressRequest() {
    }

    /** Đổ dữ liệu từ địa chỉ đang có ra form khi bấm "Sửa". */
    public static SavedAddressRequest from(SavedAddress address) {
        SavedAddressRequest request = new SavedAddressRequest();
        request.setLabel(address.getLabel());
        request.setContactName(address.getContactName());
        request.setContactPhone(address.getContactPhone());
        request.setAddressLine(address.getAddressLine());
        request.setAddressType(address.getAddressType());
        request.setDefaultAddress(address.isDefaultAddress());
        return request;
    }

    // ----- Getter / Setter -----

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.text(label);
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.text(contactName);
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.phone(contactPhone);
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.text(addressLine);
    }

    public SavedAddress.AddressType getAddressType() {
        return addressType;
    }

    public void setAddressType(SavedAddress.AddressType addressType) {
        this.addressType = addressType;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }
}
