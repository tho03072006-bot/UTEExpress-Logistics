package vn.edu.hcmute.uteexpress.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.service.geo.AddressGeocoder;
import vn.edu.hcmute.uteexpress.service.geo.GeoPoint;
import vn.edu.hcmute.uteexpress.service.geo.impl.ArcGisAddressGeocoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Mẫu JSON lấy từ phản hồi ArcGIS thật (10/2026); không gọi mạng. */
class ArcGisAddressGeocoderTest {
    static final String POINT_ADDRESS = """
        {"candidates":[{"address":"1 Đường Võ Văn Ngân, Linh Chiểu, Thủ Đức, Hồ Chí Minh","location":{"x":106.770242018052,"y":10.849742000387},
          "score":85.49,"attributes":{"Score":85.49,"Addr_type":"PointAddress","Type":""}}]}""";
    static final String COMMUNE = """
        {"candidates":[{"address":"An Định, Vĩnh Long","location":{"x":106.372626,"y":10.07521},"score":80,"attributes":{"Addr_type":"Locality","Type":"City"}}]}""";
    static final String PROVINCE_ONLY = """
        {"candidates":[{"address":"Vĩnh Long","location":{"x":105.972302,"y":10.254256},"score":100,"attributes":{"Addr_type":"Locality","Type":"City"}}]}""";
    static final String WRONG_POI = """
        {"candidates":[{"address":"Quán Ăn","location":{"x":106.5936409,"y":10.8896145},"score":77.67,"attributes":{"Addr_type":"POI"}}]}""";
    static final String EMPTY = "{\"candidates\":[]}";

    @Test void straightLineDistanceMatchesKnownPlaces() {
        var thuDuc = new GeoPoint(10.849742, 106.770242);
        assertEquals(0, thuDuc.distanceKmTo(thuDuc), 1e-9);
        assertEquals(96.0, thuDuc.distanceKmTo(new GeoPoint(10.07521, 106.372626)), 1.5);
        assertEquals(1137, new GeoPoint(21.028279, 105.853881).distanceKmTo(new GeoPoint(10.82302, 106.62965)), 15, "Hà Nội - TP.HCM");
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(91, 0));
    }

    @Test void abbreviationsAreExpandedBecauseArcGisReturnsNothingForTpHcm() {
        assertEquals("Số 01 Võ Văn Ngân, Phường Thủ Đức, Hồ Chí Minh", invokeNormalize("Số 01 Võ Văn Ngân,Phường Thủ Đức , TP.HCM"));
        assertEquals("5 Lê Lợi, Quận 1, Hồ Chí Minh", invokeNormalize("5 Lê Lợi, Q.1, TP. Hồ Chí Minh"));
        assertEquals("12 Phường 14, Quận 10, Hồ Chí Minh", invokeNormalize("12 P.14, Q10, tphcm"));
        assertEquals("Ấp 3, Xã An Định, Vĩnh Long", invokeNormalize("Ấp 3, Xã An Định, Vĩnh Long, Việt Nam"));
        assertEquals("Số nhà 112, Tân Hòa, An Định, Vĩnh Long", invokeNormalize("  Số nhà 112,  Tân Hòa, An Định, Vĩnh Long "));
    }

    @Test void fallbackQueriesNeverDropToProvinceOnly() throws Exception {
        assertEquals(List.of("Số nhà 112, Tân Hòa, An Định, Vĩnh Long", "Tân Hòa, An Định, Vĩnh Long", "An Định, Vĩnh Long"),
                invokeQueries("Số nhà 112, Tân Hòa, An Định, Vĩnh Long"));
        assertEquals(List.of("Kho quận 9"), invokeQueries("Kho quận 9"));
    }

    @Test void acceptsStreetOrCommuneButRejectsProvinceOnlyLowScoreOrWrongProvince() throws Exception {
        assertTrue(best(POINT_ADDRESS, "ho chi minh").isPresent());
        assertEquals(10.07521, best(COMMUNE, "vinh long").orElseThrow().latitude(), 1e-6);
        assertTrue(best(PROVINCE_ONLY, "vinh long").isEmpty(), "Chỉ biết tỉnh thì lệch hàng chục km");
        assertTrue(best(WRONG_POI, "ho chi minh").isEmpty(), "Điểm khớp dưới 80");
        assertTrue(best(COMMUNE, "ho chi minh").isEmpty(), "Kết quả nằm sai tỉnh/thành đã ghi");
    }

    @Test void locateTriesShorterAddressWhenHouseNumberIsUnknownAndCachesResult() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<Object> empty = response(200, EMPTY), commune = response(200, COMMUNE);
        when(http.send(any(), any())).thenReturn(empty, empty, commune);
        var geocoder = new ArcGisAddressGeocoder(http);
        var point = geocoder.locate("Số nhà 112, Tân Hòa, An Định, Vĩnh Long");
        assertEquals(106.372626, point.longitude(), 1e-6);
        geocoder.locate("Số nhà 112, Tân Hòa, An Định, Vĩnh Long");
        verify(http, times(3)).send(any(), any());
    }

    @Test void unknownAddressAndServiceOutageAreDifferentErrors() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<Object> empty = response(200, EMPTY);
        when(http.send(any(), any())).thenReturn(empty);
        assertThrows(AddressGeocoder.AddressNotFoundException.class, () -> new ArcGisAddressGeocoder(http).locate("abc xyz"));
        HttpClient down = mock(HttpClient.class);
        HttpResponse<Object> error = response(503, "Service Unavailable");
        when(down.send(any(), any())).thenReturn(error);
        assertThrows(AddressGeocoder.GeocodingUnavailableException.class, () -> new ArcGisAddressGeocoder(down).locate("1 Võ Văn Ngân, Thủ Đức, Hồ Chí Minh"));
        HttpClient broken = mock(HttpClient.class);
        when(broken.send(any(), any())).thenThrow(new java.net.http.HttpTimeoutException("timeout"));
        assertThrows(AddressGeocoder.GeocodingUnavailableException.class, () -> new ArcGisAddressGeocoder(broken).locate("1 Võ Văn Ngân, Thủ Đức, Hồ Chí Minh"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<Object> response(int status, String body) {
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        return response;
    }
    private static java.util.Optional<GeoPoint> best(String json, String province) throws Exception {
        var method = ArcGisAddressGeocoder.class.getDeclaredMethod("best", com.fasterxml.jackson.databind.JsonNode.class, String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked") var result = (java.util.Optional<GeoPoint>) method.invoke(null, new ObjectMapper().readTree(json).path("candidates"), province);
        return result;
    }
    private static String invokeNormalize(String value) {
        try { var method = ArcGisAddressGeocoder.class.getDeclaredMethod("normalize", String.class); method.setAccessible(true); return (String) method.invoke(null, value); }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    @SuppressWarnings("unchecked")
    private static List<String> invokeQueries(String value) throws Exception {
        var method = ArcGisAddressGeocoder.class.getDeclaredMethod("queries", String.class); method.setAccessible(true);
        return (List<String>) method.invoke(null, value);
    }
}
