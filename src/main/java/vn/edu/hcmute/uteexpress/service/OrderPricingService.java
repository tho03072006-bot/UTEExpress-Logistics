package vn.edu.hcmute.uteexpress.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService;
import vn.edu.hcmute.uteexpress.service.geo.AddressGeocoder;
import vn.edu.hcmute.uteexpress.service.geo.GeoPoint;

/**
 * Cước của một đơn = cước dịch vụ (bảng giá Aiven theo dịch vụ + cân nặng) + phụ phí khoảng cách đường chim bay
 * giữa địa chỉ lấy hàng và địa chỉ giao hàng (bậc phụ phí ở FeeEstimateService.distanceFee).
 *
 * Luôn tính lại ở server từ chính hai địa chỉ; không nhận số km/cước từ trình duyệt.
 */
@Service
public class OrderPricingService {
    public record Pricing(BigDecimal baseFee, BigDecimal distanceKm, BigDecimal distanceFee, BigDecimal total) { }

    private final ShippingTariffService tariffs;
    private final AddressGeocoder geocoder;

    public OrderPricingService(ShippingTariffService tariffs, AddressGeocoder geocoder) {
        this.tariffs = tariffs; this.geocoder = geocoder;
    }

    /** @throws IllegalStateException kèm thông báo tiếng Việt để hiện lại trên form (địa chỉ không rõ, Hỏa tốc quá xa...). */
    public Pricing price(String senderAddress, String receiverAddress, Order.ServiceType serviceType, double weightKg) {
        BigDecimal base = tariffs.quote(serviceType, weightKg);
        GeoPoint from = locate(senderAddress, "lấy hàng"), to = locate(receiverAddress, "giao hàng");
        // Làm tròn 0,1 km trước khi tính phụ phí để số km hiện cho khách và số tiền luôn khớp nhau.
        BigDecimal km = BigDecimal.valueOf(from.distanceKmTo(to)).setScale(1, RoundingMode.HALF_UP);
        if (serviceType == Order.ServiceType.SUPER_EXPRESS && km.doubleValue() > FeeEstimateService.SUPER_EXPRESS_MAX_KM)
            throw new IllegalStateException("Hỏa tốc chỉ nhận đơn trong bán kính 30 km (hai địa chỉ cách khoảng "
                    + km.toPlainString().replace('.', ',') + " km). Hãy chọn Nhanh hoặc Tiêu chuẩn.");
        BigDecimal distanceFee = FeeEstimateService.distanceFee(km.doubleValue(), serviceType);
        return new Pricing(base, km, distanceFee, base.add(distanceFee));
    }

    private GeoPoint locate(String address, String role) {
        try { return geocoder.locate(address); }
        catch (AddressGeocoder.AddressNotFoundException exception) {
            throw new AddressGeocoder.AddressNotFoundException("Không xác định được vị trí địa chỉ " + role
                    + ". Hãy ghi rõ số nhà, đường, phường/xã và tỉnh/thành (ví dụ: 1 Võ Văn Ngân, Thủ Đức, Hồ Chí Minh).");
        }
    }
}
