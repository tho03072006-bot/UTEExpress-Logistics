package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Du lieu form "Danh gia dich vu". Tep dinh kem nhan bang MultipartFile,
 * duong dan tep sau khi luu moi duoc ghi xuong CSDL (xem FileStorageService).
 */
public class ServiceReviewRequest {

    @NotNull(message = "Vui lòng chọn số sao")
    @Min(value = 1, message = "Số sao từ 1 đến 5")
    @Max(value = 5, message = "Số sao từ 1 đến 5")
    private Integer rating;

    @NotBlank(message = "Vui lòng viết nhận xét về dịch vụ")
    @Size(min = 50, max = 1000,
          message = "Nhận xét phải dài ít nhất 50 ký tự và không quá 1000 ký tự")
    private String content;

    /** Khong bat buoc - de trong thi danh gia khong co anh/video dinh kem. */
    private MultipartFile mediaFile;

    public ServiceReviewRequest() {
    }

    // ----- Getter / Setter -----

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MultipartFile getMediaFile() {
        return mediaFile;
    }

    public void setMediaFile(MultipartFile mediaFile) {
        this.mediaFile = mediaFile;
    }
}
