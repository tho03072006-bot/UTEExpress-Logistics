package vn.edu.hcmute.uteexpress.dto.profile;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.hcmute.uteexpress.entity.UserProfileDetail;

/** Form "Thông tin cá nhân" ở trang tài khoản của người gửi và Shipper. Mọi trường đều không bắt buộc. */
public class ProfileDetailForm {

    /** Gợi ý tỉnh/thành (34 đơn vị hành chính cấp tỉnh); ô nhập vẫn cho gõ tự do. */
    public static final List<String> PROVINCES = List.of(
            "Hà Nội", "TP. Hồ Chí Minh", "Hải Phòng", "Đà Nẵng", "Cần Thơ", "Huế",
            "An Giang", "Bắc Ninh", "Cà Mau", "Cao Bằng", "Đắk Lắk", "Điện Biên", "Đồng Nai", "Đồng Tháp",
            "Gia Lai", "Hà Tĩnh", "Hưng Yên", "Khánh Hòa", "Lai Châu", "Lâm Đồng", "Lạng Sơn", "Lào Cai",
            "Nghệ An", "Ninh Bình", "Phú Thọ", "Quảng Ngãi", "Quảng Ninh", "Quảng Trị", "Sơn La",
            "Tây Ninh", "Thái Nguyên", "Thanh Hóa", "Tuyên Quang", "Vĩnh Long");

    @PastOrPresent(message = "Ngày sinh không được ở tương lai")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    private UserProfileDetail.Gender gender;

    @Size(max = 160, message = "Giới thiệu tối đa 160 ký tự")
    private String bio;

    @Size(max = 60, message = "Tỉnh/thành tối đa 60 ký tự")
    private String province;

    @Size(max = 200, message = "Địa chỉ tối đa 200 ký tự")
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
