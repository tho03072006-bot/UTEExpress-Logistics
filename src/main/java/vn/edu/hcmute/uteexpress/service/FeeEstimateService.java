package vn.edu.hcmute.uteexpress.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.entity.Order;

/**
 * Báo giá tham khảo ở trang Ước tính cước: cước gốc (đúng công thức khi tạo đơn) + phụ phí khoảng cách.
 *
 * Phụ phí khoảng cách CHỈ dùng để tham khảo, luồng tạo đơn vẫn tính cước theo dịch vụ + cân nặng.
 * Bậc phụ phí (đồng, làm tròn tới 100 đ):
 *   0 - 5 km      : miễn phí
 *   5 - 30 km     : 1.000 đ/km (tính từ km thứ 5)
 *   30 - 300 km   : 25.000 đ + 300 đ/km (tính từ km thứ 30)
 *   trên 300 km   : 106.000 đ + 100 đ/km (tính từ km thứ 300)
 * Hệ số theo dịch vụ: Tiêu chuẩn x1, Nhanh x1,3, Hỏa tốc x1,6. Hỏa tốc chỉ nhận trong 30 km.
 */
@Service
public class FeeEstimateService {
    public static final double SUPER_EXPRESS_MAX_KM = 30;
    private final OrderService orders;

    public FeeEstimateService(OrderService orders) { this.orders = orders; }

    public record Quote(Order.ServiceType serviceType, BigDecimal baseFee, BigDecimal distanceFee, BigDecimal total,
            String deliveryTime, boolean available, String note) { }

    /** Báo giá cho một dịch vụ hoặc cả ba (serviceType = null). distanceKm = null: không tính phụ phí. */
    public List<Quote> quotes(double weightKg, Double distanceKm, Order.ServiceType serviceType) {
        if (weightKg <= 0 || weightKg > 100) throw new IllegalArgumentException("Khối lượng phải trong khoảng 0 - 100 kg.");
        if (distanceKm != null && (distanceKm <= 0 || distanceKm > 2000))
            throw new IllegalArgumentException("Khoảng cách phải trong khoảng 0 - 2.000 km.");
        List<Quote> result = new ArrayList<>();
        for (Order.ServiceType type : Order.ServiceType.values()) {
            if (serviceType != null && type != serviceType) continue;
            result.add(quote(type, weightKg, distanceKm));
        }
        return result;
    }

    Quote quote(Order.ServiceType type, double weightKg, Double distanceKm) {
        BigDecimal base = orders.estimateFee(type, weightKg).setScale(0, RoundingMode.HALF_UP);
        if (type == Order.ServiceType.SUPER_EXPRESS && distanceKm != null && distanceKm > SUPER_EXPRESS_MAX_KM) {
            return new Quote(type, base, BigDecimal.ZERO, null, "Không hỗ trợ", false,
                    "Hỏa tốc chỉ nhận đơn trong bán kính 30 km. Hãy chọn Nhanh hoặc Tiêu chuẩn.");
        }
        BigDecimal distanceFee = distanceKm == null ? BigDecimal.ZERO : distanceFee(distanceKm, type);
        return new Quote(type, base, distanceFee, base.add(distanceFee), deliveryTime(type, distanceKm), true, null);
    }

    static BigDecimal distanceFee(double km, Order.ServiceType type) {
        double fee;
        if (km <= 5) fee = 0;
        else if (km <= 30) fee = (km - 5) * 1000;
        else if (km <= 300) fee = 25000 + (km - 30) * 300;
        else fee = 106000 + (km - 300) * 100;
        double factor = switch (type) { case STANDARD -> 1.0; case EXPRESS -> 1.3; case SUPER_EXPRESS -> 1.6; };
        return BigDecimal.valueOf(fee * factor).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    static String deliveryTime(Order.ServiceType type, Double km) {
        if (km == null) return switch (type) { case STANDARD -> "3 - 5 ngày"; case EXPRESS -> "1 - 2 ngày"; case SUPER_EXPRESS -> "Trong ngày"; };
        return switch (type) {
            case SUPER_EXPRESS -> "Trong ngày (2 - 4 giờ)";
            case EXPRESS -> km <= 30 ? "Trong ngày" : km <= 300 ? "1 ngày" : "1 - 2 ngày";
            case STANDARD -> km <= 30 ? "1 - 2 ngày" : km <= 300 ? "2 - 3 ngày" : "3 - 5 ngày";
        };
    }
}
