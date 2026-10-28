package vn.edu.hcmute.uteexpress.service.routing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import vn.edu.hcmute.uteexpress.entity.Order.ServiceType;
import vn.edu.hcmute.uteexpress.entity.routing.ProvinceArea;

/** Khoảng cách giữa tâm khu vực xấp xỉ; không phải khoảng cách đường bộ hay vị trí nhà shipper. */
public final class RouteDistance {
    private RouteDistance() { }
    public static double kilometers(ProvinceArea from, ProvinceArea to) {
        double lat1 = Math.toRadians(from.getLat().doubleValue()), lat2 = Math.toRadians(to.getLat().doubleValue());
        double dLat = lat2 - lat1, dLng = Math.toRadians(to.getLng().doubleValue() - from.getLng().doubleValue());
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLng / 2), 2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
    }
    public static BigDecimal surcharge(double km, ServiceType service) {
        if (!Double.isFinite(km) || km < 0 || service == null) throw new IllegalArgumentException("Khoảng cách hoặc dịch vụ không hợp lệ.");
        double fee = km <= 5 ? 0 : km <= 30 ? (km - 5) * 1000
                : km <= 300 ? 25000 + (km - 30) * 300 : 106000 + (km - 300) * 100;
        double factor = switch (service) { case STANDARD -> 1; case EXPRESS -> 1.3; case SUPER_EXPRESS -> 1.6; };
        return BigDecimal.valueOf(fee * factor).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }
}
