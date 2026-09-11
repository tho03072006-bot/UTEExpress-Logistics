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

    @NotBlank(message = "Vui lòng nhập nhãn gợi nhớ cho địa chỉ")
    @Size(max = 50, message = "Nhãn địa chỉ tối đa 50 ký tự")
    private String label;

    @NotBlank(message = "Vui lòng nhập họ tên người liên hệ")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String contactName;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^0[0-9]{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và có 10 - 11 chữ số")
    private String contactPhone;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
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
        this.label = label;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
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
