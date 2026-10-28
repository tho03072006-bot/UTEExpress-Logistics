package vn.edu.hcmute.uteexpress.service.geo;

/** Tọa độ WGS84 của một địa chỉ; chỉ giữ trong bộ nhớ, không lưu xuống database. */
public record GeoPoint(double latitude, double longitude) {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    public GeoPoint {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
            throw new IllegalArgumentException("Tọa độ không hợp lệ.");
    }

    /** Khoảng cách đường chim bay (công thức haversine), đơn vị km. */
    public double distanceKmTo(GeoPoint other) {
        double dLat = Math.toRadians(other.latitude - latitude), dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
