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
    @Pattern(regexp = "[^\\p{Cntrl}]*", message = "Nhãn không được chứa ký tự điều khiển")
    private String label;

    @NotBlank(message = "Vui lòng nhập họ tên người liên hệ")
    @Size(min = 2, max = 100, message = "Họ tên từ 2–100 ký tự")
    @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M} .'-]+", message = "Tên người liên hệ chỉ gồm chữ và khoảng trắng")
    private String contactName;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "0[35789][0-9]{8}", message = "Nhập số di động Việt Nam 10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09")
    private String contactPhone;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết")
    @Size(min = 5, max = 255, message = "Địa chỉ từ 5–255 ký tự")
    @Pattern(regexp = "[^\\p{Cntrl}]*", message = "Địa chỉ không được chứa ký tự điều khiển")
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
        this.label = label == null ? null : label.strip().replaceAll(" {2,}", " ");
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName == null ? null : contactName.strip().replaceAll(" {2,}", " ");
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        String value = contactPhone == null ? null : contactPhone.strip().replace(" ", "").replace("-", "").replace(".", "");
        if (value != null && value.startsWith("+84")) value = "0" + value.substring(3);
        this.contactPhone = value;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine == null ? null : addressLine.strip().replaceAll(" {2,}", " ");
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
