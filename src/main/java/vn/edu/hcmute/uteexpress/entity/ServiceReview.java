package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Đánh giá chất lượng dịch vụ giao hàng của một vận đơn - TV1 phụ trách (việc 4).
 *
 * Mỗi vận đơn chỉ được đánh giá một lần, và chỉ khi đơn đã ở trạng thái DELIVERED
 * (giao thành công) - không cho đánh giá đơn còn đang trên đường hay đã huỷ.
 *
 * Tách entity riêng thay vì thêm cột vào Order: Order.java là file dùng chung cả nhóm,
 * quy ước là không tự sửa. Tách ra cũng đúng nghiệp vụ hơn - phần lớn đơn sẽ không bao giờ
 * được đánh giá, nhét thêm 4-5 cột rỗng vào bảng vận đơn là lãng phí.
 */
@Entity
@Table(name = "service_review")
public class ServiceReview {

    /** Số ký tự tối thiểu của nội dung đánh giá, theo đúng yêu cầu đề bài. */
    public static final int MIN_CONTENT_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    /**
     * Người viết đánh giá. Về lý thuyết có thể suy ra từ order.sender, nhưng lưu thẳng ở đây
     * để truy vấn "các đánh giá của tôi" khỏi phải join qua bảng vận đơn.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** Số sao từ 1 đến 5. */
    @Column(nullable = false)
    private int rating;

    @Nationalized
    @Column(nullable = false, length = 1000)
    private String content;

    /**
     * Đường dẫn công khai tới ảnh/video đính kèm, dạng "/uploads/danh-gia/<tên file>".
     * Chỉ lưu đường dẫn trong CSDL, file thật nằm ngoài thư mục mã nguồn (xem FileStorageService).
     * null nghĩa là đánh giá không đính kèm gì.
     */
    @Column(name = "media_path", length = 255)
    private String mediaPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 10)
    private MediaType mediaType;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Loại tệp đính kèm - quyết định hiển thị bằng thẻ img hay thẻ video. */
    public enum MediaType {
        IMAGE, VIDEO
    }

    public ServiceReview() {
    }

    /** Tiện cho template: có đính kèm hay không, khỏi phải kiểm tra null hai lần. */
    public boolean hasMedia() {
        return mediaPath != null && mediaType != null;
    }

    // ----- Getter / Setter -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public void setMediaPath(String mediaPath) {
        this.mediaPath = mediaPath;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
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
