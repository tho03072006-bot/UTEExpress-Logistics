package vn.edu.hcmute.uteexpress.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import vn.edu.hcmute.uteexpress.service.geo.AddressGeocoder;
import vn.edu.hcmute.uteexpress.service.geo.GeoPoint;

/**
 * Chỉ test: không gọi ArcGIS. Mặc định mọi địa chỉ cùng một điểm (0 km, không phụ phí) để các test cước cũ giữ nguyên;
 * test cần khoảng cách thì đăng ký tọa độ riêng. Chứa "KHONG-TIM-THAY" = địa chỉ không rõ, "BAN-DO-LOI" = dịch vụ lỗi.
 */
public class FixedAddressGeocoder implements AddressGeocoder {
    public static final GeoPoint DEFAULT = new GeoPoint(10.8497, 106.7702);
    private final Map<String, GeoPoint> points = new ConcurrentHashMap<>();

    public void register(String address, GeoPoint point) { points.put(address.strip(), point); }
    public void clear() { points.clear(); }

    @Override public GeoPoint locate(String address) {
        if (address == null || address.isBlank() || address.contains("KHONG-TIM-THAY")) throw new AddressNotFoundException("Không xác định được vị trí");
        if (address.contains("BAN-DO-LOI")) throw new GeocodingUnavailableException();
        return points.getOrDefault(address.strip(), DEFAULT);
    }
}
