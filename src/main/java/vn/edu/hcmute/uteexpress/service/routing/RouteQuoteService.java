package vn.edu.hcmute.uteexpress.service.routing;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.Order.ServiceType;
import vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode;
import vn.edu.hcmute.uteexpress.entity.routing.ProvinceArea;
import vn.edu.hcmute.uteexpress.repository.routing.RouteTariffRepository;
import vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService;
import vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService;

/** Một báo giá dùng cho ước tính, tạo đơn và xác nhận giỏ đơn; mã giảm giá áp sau tổng này. */
@Service @Transactional(readOnly = true)
public class RouteQuoteService {
    public static final double SUPER_EXPRESS_MAX_KM = 30;
    private final RoutingCatalogService catalog;
    private final ShippingTariffService baseTariffs;
    private final RouteTariffRepository routes;
    private final OperationsPolicyService policies;
    public RouteQuoteService(RoutingCatalogService catalog, ShippingTariffService baseTariffs, RouteTariffRepository routes,
            OperationsPolicyService policies) {
        this.catalog = catalog; this.baseTariffs = baseTariffs; this.routes = routes; this.policies = policies;
    }
    /** dropOffDiscount là số thực giảm (không vượt tổng trước giảm), total = gốc + khoảng cách + tuyến - giảm. */
    public record Quote(ServiceType serviceType, ProvinceArea pickupArea, ProvinceArea deliveryArea,
            double distanceKm, boolean interprovincial, BigDecimal baseFee, BigDecimal distanceFee,
            BigDecimal routeFee, BigDecimal dropOffDiscount, BigDecimal total, String leadTimeText, PickupMode pickupMode) { }
    public java.util.List<Quote> quotes(vn.edu.hcmute.uteexpress.dto.FeeEstimateRequest form) {
        var pickup = catalog.requireArea(form.getPickupProvinceCode(), form.getPickupAreaCode());
        var delivery = catalog.requireArea(form.getDeliveryProvinceCode(), form.getDeliveryAreaCode());
        if (form.getWeightKg() == null) throw new IllegalArgumentException("Nhập khối lượng để tính cước.");
        if (form.getServiceType() != null) return java.util.List.of(quote(form.getServiceType(), form.getWeightKg(), form.getPickupAreaCode(), form.getDeliveryAreaCode(), form.getPickupMode()));
        boolean superAvailable = Objects.equals(pickup.getBranch().getId(), delivery.getBranch().getId())
                && RouteDistance.kilometers(pickup, delivery) <= SUPER_EXPRESS_MAX_KM;
        return java.util.Arrays.stream(ServiceType.values()).filter(t -> t != ServiceType.SUPER_EXPRESS || superAvailable)
                .map(t -> quote(t, form.getWeightKg(), form.getPickupAreaCode(), form.getDeliveryAreaCode(), form.getPickupMode())).toList();
    }
    public Quote quote(ServiceType service, double kg, String pickupArea, String deliveryArea) {
        return quote(service, kg, pickupArea, deliveryArea, PickupMode.COURIER);
    }
    public Quote quote(ServiceType service, double kg, String pickupArea, String deliveryArea, PickupMode mode) {
        if (service == null || !Double.isFinite(kg) || kg < 0.01 || kg > 100)
            throw new IllegalArgumentException("Chọn dịch vụ và khối lượng từ 0,01 đến 100 kg.");
        var pickup = catalog.requireArea(pickupArea); var delivery = catalog.requireArea(deliveryArea);
        boolean inter = !Objects.equals(pickup.getBranch().getId(), delivery.getBranch().getId());
        double km = RouteDistance.kilometers(pickup, delivery);
        if (service == ServiceType.SUPER_EXPRESS && (inter || km > SUPER_EXPRESS_MAX_KM))
            throw new IllegalArgumentException("Hỏa tốc chỉ nhận trong cùng chi nhánh và không quá 30 km.");
        var tariff = routes.findByOriginBranch_IdAndDestBranch_IdAndServiceType(
                pickup.getBranch().getId(), delivery.getBranch().getId(), service)
                .orElseThrow(() -> new IllegalStateException("Chưa cấu hình cước tuyến này. Vui lòng liên hệ Admin."));
        var base = baseTariffs.quote(service, kg);
        var distance = inter ? BigDecimal.ZERO : RouteDistance.surcharge(km, service);
        var route = inter ? tariff.getSurcharge() : BigDecimal.ZERO;
        var subtotal = base.add(distance).add(route);
        var pickupMode = mode == null ? PickupMode.COURIER : mode;
        var discount = pickupMode == PickupMode.DROP_OFF ? policies.current().getDropOffDiscount().min(subtotal).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        return new Quote(service, pickup, delivery, km, inter, base, distance, route, discount, subtotal.subtract(discount),
                tariff.getLeadTimeText(), pickupMode);
    }
}
