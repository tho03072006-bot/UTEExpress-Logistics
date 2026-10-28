package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Entity địa chỉ đã lưu (sổ địa chỉ) của người gửi hàng - TV1 phụ trách.
 *
 * Quan hệ tới AppUser để một chiều (SavedAddress -> AppUser) bằng @ManyToOne, KHÔNG thêm
 * @OneToMany ngược lại trong AppUser.java, vì AppUser.java là entity dùng chung cả nhóm
 * (quy ước: không sửa trực tiếp file đó). Muốn lấy danh sách địa chỉ của một user thì
 * truy vấn qua SavedAddressRepository.findByUser(...) - kết quả giống hệt @OneToMany
 * nhưng không đụng vào file của bạn khác.
 */
@Entity
@Table(name = "saved_address")
public class SavedAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chủ sở hữu địa chỉ - mỗi người dùng chỉ nhìn thấy sổ địa chỉ của chính mình. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /**
     * Nhãn gợi nhớ do người dùng tự đặt: "Nhà riêng", "Kho quận 9", "Mẹ ở quê"...
     *
     * @Nationalized bắt Hibernate sinh cột nvarchar thay vì varchar trên SQL Server.
     * Thiếu annotation này thì tiếng Việt có dấu lưu xuống DB sẽ thành dấu "?"
     * (varchar chỉ chứa được bảng mã 1 byte). Mọi cột chữ có thể chứa tiếng Việt
     * trong entity mới đều phải đánh dấu như vậy.
     */
    @Nationalized
    @Column(nullable = false, length = 50)
    private String label;

    @Nationalized
    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    /** Chỉ chứa chữ số nên không cần nvarchar. */
    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Nationalized
    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    /** Địa chỉ này dùng để lấy hàng (SENDER) hay để giao hàng (RECEIVER). */
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType = AddressType.SENDER;

    /** Mỗi người dùng chỉ có tối đa 1 địa chỉ mặc định cho mỗi loại - ràng buộc xử lý ở Service. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Loại địa chỉ. displayName là phần hiển thị tiếng Việt ra giao diện,
     * còn tên hằng (SENDER/RECEIVER) mới là giá trị lưu xuống cột address_type.
     */
    public enum AddressType {
        SENDER("Địa chỉ gửi hàng"),
        RECEIVER("Địa chỉ người nhận");

        private final String displayName;

        AddressType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public SavedAddress() {
    }

    // ----- Getter / Setter -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

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

    public AddressType getAddressType() {
        return addressType;
    }

    public void setAddressType(AddressType addressType) {
        this.addressType = addressType;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
