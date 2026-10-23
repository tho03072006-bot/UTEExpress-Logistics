package vn.edu.hcmute.uteexpress.service.routing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository;
import vn.edu.hcmute.uteexpress.repository.routing.RouteTariffRepository;

@Service @Transactional(readOnly = true)
public class OrderRoutingService {
    private final RoutingCatalogService catalog;
    private final RouteQuoteService quotes;
    private final OrderRouteRepository routes;
    private final RouteTariffRepository tariffs;
    public OrderRoutingService(RoutingCatalogService catalog, RouteQuoteService quotes, OrderRouteRepository routes, RouteTariffRepository tariffs) {
        this.catalog = catalog; this.quotes = quotes; this.routes = routes; this.tariffs = tariffs;
    }
    /** Tóm tắt tuyến cho người gửi và Guest: chi nhánh gửi/nhận, vị trí hiện tại, thời gian dự kiến, quầy nhận hàng. */
    public record RouteSummary(String originName, String destName, boolean interprovincial, String location,
            String leadTime, boolean dropOff, String counterAddress) { }
    public RouteQuoteService.Quote quote(OrderCreateRequest form) {
        catalog.requireArea(form.getPickupProvinceCode(), form.getPickupAreaCode());
        catalog.requireArea(form.getDeliveryProvinceCode(), form.getDeliveryAreaCode());
        return quotes.quote(form.getServiceType(), form.getWeightKg(), form.getPickupAreaCode(), form.getDeliveryAreaCode(), form.getPickupMode());
    }
    public String fullAddress(String street, ProvinceArea area) {
        String address = street.strip() + ", " + area.getAreaName() + ", " + provinceName(area.getProvinceCode());
        if (address.length() > 255) throw new IllegalArgumentException("Địa chỉ gồm số nhà, đường, khu vực và tỉnh/thành không được quá 255 ký tự.");
        return address;
    }
    /** Tuyến của đơn; đơn cũ tạo trước khi có chi nhánh thì rỗng. */
    public java.util.Optional<OrderRoute> routeOf(Long orderId) {
        return orderId == null ? java.util.Optional.empty() : routes.findByOrder_Id(orderId);
    }
    public java.util.Optional<RouteSummary> summary(Order order) {
        if (order == null) return java.util.Optional.empty();
        return routeOf(order.getId()).map(route -> {
            var origin = route.getOriginBranch(); var dest = route.getDestBranch();
            String lead = tariffs.findByOriginBranch_IdAndDestBranch_IdAndServiceType(origin.getId(), dest.getId(), order.getServiceType())
                    .map(t -> t.getLeadTimeText()).orElse(null);
            return new RouteSummary(origin.getName(), dest.getName(), route.isInterprovincial(), location(order, route), lead,
                    route.isDropOff(), route.isDropOff() ? origin.getHubAddress() : null);
        });
    }
    /** Vị trí hiện tại kèm tuyến (đơn liên tỉnh) cho email và trợ lý; null nếu trạng thái đã tự nói rõ. */
    public static String describe(Order order, OrderRoute route) {
        String location = location(order, route);
        if (location == null) return null;
        return route.isInterprovincial()
                ? location + " (tuyến " + route.getOriginBranch().getName() + " → " + route.getDestBranch().getName() + ")"
                : location + " (nội tỉnh " + route.getOriginBranch().getName() + ")";
    }
    /** Vị trí theo chặng; trạng thái kết thúc (đã giao, hủy, hoàn) đã tự nói rõ nên trả null. */
    public static String location(Order order, OrderRoute route) {
        var origin = route.getOriginBranch().getName(); var dest = route.getDestBranch().getName();
        return switch (order.getStatus()) {
            case PENDING_PICKUP -> route.isDropOff() ? "Chờ người gửi mang hàng tới quầy " + origin : "Chờ Shipper " + origin + " đến lấy hàng";
            case PICKED_UP -> switch (route.getStage()) {
                case PICKUP -> route.isInterprovincial() ? "Shipper đã lấy hàng, đang về kho " + origin : "Shipper đã lấy hàng";
                case ORIGIN_HUB -> "Tại kho " + origin + ", chờ xếp chuyến";
                case LINEHAUL -> "Đang trung chuyển từ " + origin + " tới " + dest;
                case DEST_HUB -> "Tại kho " + dest + ", chờ phân công Shipper giao";
                case LAST_MILE -> "Tại kho " + dest + ", Shipper sắp đi giao";
            };
            case IN_TRANSIT -> "Shipper " + dest + " đang giao";
            case FAILED -> !route.isReturnLeg() ? "Giao chưa thành công, kho " + dest + " đang xử lý" : switch (route.getStage()) {
                case DEST_HUB -> "Hàng hoàn tại kho " + dest + ", chờ chuyến về " + origin;
                case LINEHAUL -> "Hàng hoàn đang trung chuyển từ " + dest + " về " + origin;
                default -> "Hàng hoàn đã về kho " + origin + ", chờ trả người gửi";
            };
            default -> null;
        };
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void attach(Order order, RouteQuoteService.Quote quote) {
        var route = new OrderRoute(order, quote.pickupArea(), quote.deliveryArea());
        route.choosePickupMode(quote.pickupMode());
        routes.save(route);
    }
    public static String provinceName(String code) {
        return switch (code) { case "79" -> "TP. Hồ Chí Minh"; case "01" -> "Hà Nội"; case "48" -> "Đà Nẵng"; default -> code; };
    }
}
