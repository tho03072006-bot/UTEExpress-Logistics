package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Nationalized;

/**
 * Thông tin cá nhân mở rộng của người gửi / Shipper: ảnh đại diện, ngày sinh, giới tính,
 * giới thiệu, địa chỉ liên hệ và tùy chọn email. Bảng riêng (mỗi tài khoản tối đa một dòng)
 * để không phải sửa bảng app_user dùng chung; tài khoản chưa có dòng nào thì dùng giá trị mặc định.
 */
@Entity
@Table(name = "user_profile_detail")
public class UserProfileDetail {

    public enum Gender {
        MALE("Nam"), FEMALE("Nữ"), OTHER("Khác");
        private final String label;
        Gender(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Version @Column(nullable = false)
    private long revision;

    /** Mã tham chiếu ảnh trên Cloudinary (dạng cld1:...), không phải URL công khai. */
    @Column(name = "avatar_asset", length = 255)
    private String avatarAsset;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Gender gender;

    @Nationalized @Column(length = 160)
    private String bio;

    @Nationalized @Column(length = 60)
    private String province;

    @Nationalized @Column(name = "address_line", length = 200)
    private String addressLine;

    /** Nhận email khi vận đơn lấy hàng thất bại, giao thành công/thất bại hoặc hoàn trả. */
    @Column(name = "notify_order_email", nullable = false)
    private boolean notifyOrderEmail = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserProfileDetail() { }

    public UserProfileDetail(AppUser user, LocalDateTime now) {
        this.user = user;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public long getRevision() { return revision; }
    public String getAvatarAsset() { return avatarAsset; }
    public LocalDate getBirthDate() { return birthDate; }
    public Gender getGender() { return gender; }
    public String getBio() { return bio; }
    public String getProvince() { return province; }
    public String getAddressLine() { return addressLine; }
    public boolean isNotifyOrderEmail() { return notifyOrderEmail; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public boolean hasAvatar() { return avatarAsset != null && !avatarAsset.isBlank(); }

    public void changeAvatar(String asset, LocalDateTime now) { this.avatarAsset = asset; this.updatedAt = now; }

    public void changeDetails(LocalDate birthDate, Gender gender, String bio, String province, String addressLine,
            LocalDateTime now) {
        this.birthDate = birthDate;
        this.gender = gender;
        this.bio = bio;
        this.province = province;
        this.addressLine = addressLine;
        this.updatedAt = now;
    }

    public void changeNotifications(boolean notifyOrderEmail, LocalDateTime now) {
        this.notifyOrderEmail = notifyOrderEmail;
        this.updatedAt = now;
    }
}
