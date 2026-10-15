package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.List;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperRoute;

/** Cho test ở package khác gọi các hàm tĩnh nội bộ của ShipperRouteService. */
public final class ShipperRouteServiceTestAccess {
    private ShipperRouteServiceTestAccess() { }
    public static List<ShipperRoute.Leg> legs(List<ShipperRoute.Stop> stops) { return ShipperRouteService.legs(stops); }
    public static List<ShipperRoute.Leg> mobileLegs(List<ShipperRoute.Stop> stops) {
        return ShipperRouteService.legs(stops, ShipperRouteService.MAX_STOPS_PER_MOBILE_LEG);
    }
    public static String clean(String address) { return ShipperRouteService.clean(address); }
}
