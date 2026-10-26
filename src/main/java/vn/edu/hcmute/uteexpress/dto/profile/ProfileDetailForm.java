package vn.edu.hcmute.uteexpress.dto.profile;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.hcmute.uteexpress.entity.UserProfileDetail;

/** Form "Thông tin cá nhân" ở trang tài khoản của người gửi và Shipper. Mọi trường đều không bắt buộc. */
public class ProfileDetailForm {

    /**
     * Gợi ý tỉnh/thành theo bản đồ hành chính cũ (63 tỉnh/thành, trước 01/07/2025), thống nhất với khu vực giao nhận
     * và chi nhánh của hệ thống; ô nhập vẫn cho gõ tự do.
     */
    public static final List<String> PROVINCES = List.of(
            "Hà Nội", "TP. Hồ Chí Minh", "Hải Phòng", "Đà Nẵng", "Cần Thơ",
            "An Giang", "Bà Rịa - Vũng Tàu", "Bắc Giang", "Bắc Kạn", "Bạc Liêu", "Bắc Ninh", "Bến Tre", "Bình Định",
            "Bình Dương", "Bình Phước", "Bình Thuận", "Cà Mau", "Cao Bằng", "Đắk Lắk", "Đắk Nông", "Điện Biên",
            "Đồng Nai", "Đồng Tháp", "Gia Lai", "Hà Giang", "Hà Nam", "Hà Tĩnh", "Hải Dương", "Hậu Giang",
            "Hòa Bình", "Hưng Yên", "Khánh Hòa", "Kiên Giang", "Kon Tum", "Lai Châu", "Lâm Đồng", "Lạng Sơn",
            "Lào Cai", "Long An", "Nam Định", "Nghệ An", "Ninh Bình", "Ninh Thuận", "Phú Thọ", "Phú Yên",
            "Quảng Bình", "Quảng Nam", "Quảng Ngãi", "Quảng Ninh", "Quảng Trị", "Sóc Trăng", "Sơn La", "Tây Ninh",
            "Thái Bình", "Thái Nguyên", "Thanh Hóa", "Thừa Thiên Huế", "Tiền Giang", "Trà Vinh", "Tuyên Quang",
            "Vĩnh Long", "Vĩnh Phúc", "Yên Bái");

    @PastOrPresent(message = "Ngày sinh không được ở tương lai")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    private UserProfileDetail.Gender gender;

    @Size(max = 160, message = "Giới thiệu tối đa 160 ký tự")
    @jakarta.validation.constraints.Pattern(regexp = "[^\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F\\x7F]*", message = "Giới thiệu chứa ký tự không hợp lệ")
    private String bio;

    @Size(max = 60, message = "Tỉnh thành tối đa 60 ký tự")
    @jakarta.validation.constraints.Pattern(regexp = "[^\\p{Cntrl}]*", message = "Tỉnh thành chứa ký tự không hợp lệ")
    private String province;

    @Size(max = 200, message = "Địa chỉ tối đa 200 ký tự")
    @jakarta.validation.constraints.Pattern(regexp = "[^\\p{Cntrl}]*", message = "Địa chỉ chứa ký tự không hợp lệ")
    private String addressLine;

    public static ProfileDetailForm from(UserProfileDetail detail) {
        ProfileDetailForm form = new ProfileDetailForm();
        if (detail != null) {
            form.birthDate = detail.getBirthDate();
            form.gender = detail.getGender();
            form.bio = detail.getBio();
            form.province = detail.getProvince();
            form.addressLine = detail.getAddressLine();
        }
        return form;
    }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public UserProfileDetail.Gender getGender() { return gender; }
    public void setGender(UserProfileDetail.Gender gender) { this.gender = gender; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
}
