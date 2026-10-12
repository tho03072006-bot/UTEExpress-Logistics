package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import vn.edu.hcmute.uteexpress.entity.Order;

/**
 * Dữ liệu của form ước tính cước phí ở trang công khai - TV1 phụ trách.
 *
 * Chỉ hỏi khối lượng, không hỏi loại dịch vụ: trang này trả về cước của cả ba loại
 * cùng lúc để người gửi so sánh, bắt chọn trước rồi mới cho xem giá thì phải bấm đi
 * bấm lại ba lần.
 */
public class FeeEstimateRequest {

    /**
     * CỐ Ý không đánh @NotNull: để trống nghĩa là người dùng vừa mở trang chứ chưa
     * bấm tính, lúc đó chỉ hiện form rỗng chứ không nên đỏ mặt báo lỗi ngay. Ô nhập
     * bên giao diện đã đánh "required" nên bấm nút mà bỏ trống thì trình duyệt chặn
     * trước rồi.
     *
     * Giới hạn trên 100 kg cho khớp thực tế nhận hàng: quá mức này là hàng cồng kềnh,
     * phải báo giá riêng chứ không tính theo bảng giá thường. Không chặn thì người
     * dùng gõ nhầm một số rất lớn và nhận về con số cước vô nghĩa.
     */
    @Positive(message = "Khối lượng phải lớn hơn 0")
    @DecimalMax(value = "100.0", message = "Đơn trên 100 kg vui lòng liên hệ tổng đài để được báo giá riêng")
    private Double weightKg;

    /**
     * Khoảng cách lấy - giao (km), không bắt buộc. Để trống thì chỉ tính cước gốc như khi tạo đơn;
     * có nhập thì cộng thêm phụ phí khoảng cách tham khảo (xem FeeEstimateService).
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "Khoảng cách phải lớn hơn 0 km")
    @DecimalMax(value = "2000.0", message = "Khoảng cách tối đa 2.000 km trong lãnh thổ Việt Nam")
    private Double distanceKm;

    /** Loại dịch vụ muốn xem; để trống thì so sánh cả ba loại. */
    private Order.ServiceType serviceType;

    public FeeEstimateRequest() {
    }

    // ----- Getter / Setter -----

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Order.ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(Order.ServiceType serviceType) {
        this.serviceType = serviceType;
    }
}
