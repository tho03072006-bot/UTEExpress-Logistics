package vn.edu.hcmute.uteexpress.service.geo.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.service.geo.AddressGeocoder;
import vn.edu.hcmute.uteexpress.service.geo.GeoPoint;

/**
 * Tìm tọa độ bằng ArcGIS World Geocoding (cùng dịch vụ trang lộ trình Shipper đang dùng), giới hạn trong Việt Nam.
 * Chỉ giữ kết quả tạm trong bộ nhớ, không lưu tọa độ xuống database (đơn chỉ lưu số km và phụ phí).
 *
 * Quy tắc nhận kết quả (đã thử với địa chỉ thật): điểm khớp >= 80; không nhận khi chỉ khớp tới cấp tỉnh/thành
 * (sai số hàng chục km); địa chỉ có ghi tỉnh/thành thì kết quả phải nằm trong tỉnh/thành đó. Không tìm ra số nhà
 * thì thử lại ở cấp phường/xã, quận/huyện, nhưng không bao giờ lùi tới chỉ còn tên tỉnh.
 */
@Component
public class ArcGisAddressGeocoder implements AddressGeocoder {
    static final String ENDPOINT = "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates";
    static final double MIN_SCORE = 80;
    private static final int CACHE_SIZE = 1000;
    private static final Duration FOUND_TTL = Duration.ofHours(12), MISSING_TTL = Duration.ofMinutes(10);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Viết tắt hay gặp mà ArcGIS không hiểu ("TP.HCM" trả về rỗng). */
    private static final List<Map.Entry<Pattern, String>> ABBREVIATIONS = List.of(
        Map.entry(Pattern.compile("(?iu)(?<!\\p{L})(?:(?:thành phố|tp)\\.?\\s*)?(?:hcm|hồ chí minh|ho chi minh)(?!\\p{L})"), "Hồ Chí Minh"),
        Map.entry(Pattern.compile("(?iu)(?<!\\p{L})tphcm(?!\\p{L})"), "Hồ Chí Minh"),
        Map.entry(Pattern.compile("(?iu)(?<!\\p{L})(?:tp|thành phố)\\.?\\s*(?:hn|hà nội)(?!\\p{L})"), "Hà Nội"),
        Map.entry(Pattern.compile("(?iu)(?<!\\p{L})tp\\.?\\s+(?=\\p{L})"), "Thành phố "),
        Map.entry(Pattern.compile("(?iu)(?<!\\p{L})q\\.?\\s*(?=\\d)"), "Quận "),
        Map.entry(Pattern.compile("(?iu)(?<!\\p{L})p\\.?\\s*(?=\\d)"), "Phường "));

    private record Cached(GeoPoint point, long expiresAt) { }
    private final Map<String, Cached> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) { return size() > CACHE_SIZE; }
    };
    private final HttpClient client;

    @Autowired public ArcGisAddressGeocoder() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    public ArcGisAddressGeocoder(HttpClient client) { this.client = client; }

    @Override
    public GeoPoint locate(String address) {
        String normalized = normalize(address);
        if (normalized.isEmpty()) throw notFound();
        Cached cached;
        synchronized (cache) { cached = cache.get(normalized); }
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            if (cached.point() == null) throw notFound();
            return cached.point();
        }
        GeoPoint found = null;
        String province = province(normalized);
        for (String query : queries(normalized)) {
            found = best(fetch(query), province).orElse(null);
            if (found != null) break;
        }
        long ttl = (found == null ? MISSING_TTL : FOUND_TTL).toMillis();
        synchronized (cache) { cache.put(normalized, new Cached(found, System.currentTimeMillis() + ttl)); }
        if (found == null) throw notFound();
        return found;
    }

    private JsonNode fetch(String query) {
        String url = ENDPOINT + "?f=json&maxLocations=3&countryCode=VNM&outFields=Score,Addr_type,Type&SingleLine="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            var response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new GeocodingUnavailableException();
            JsonNode body = JSON.readTree(response.body());
            if (body.has("error") || !body.path("candidates").isArray()) throw new GeocodingUnavailableException();
            return body.path("candidates");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new GeocodingUnavailableException();
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new GeocodingUnavailableException();
        }
    }

    /** Ứng viên tốt nhất thỏa quy tắc; ArcGIS đã sắp theo điểm giảm dần. */
    static Optional<GeoPoint> best(JsonNode candidates, String province) {
        for (JsonNode candidate : candidates) {
            double score = candidate.path("score").asDouble(0);
            String label = candidate.path("address").asText("");
            String type = candidate.path("attributes").path("Addr_type").asText("");
            if (score < MIN_SCORE || label.isBlank()) continue;
            // "Vĩnh Long", "Ho Chi Minh"...: chỉ biết tỉnh/thành, lệch hàng chục km so với địa chỉ thật.
            if ("Locality".equals(type) && !label.contains(",")) continue;
            if (province != null && !fold(label).contains(province)) continue;
            JsonNode location = candidate.path("location");
            if (!location.path("x").isNumber() || !location.path("y").isNumber()) continue;
            try { return Optional.of(new GeoPoint(location.path("y").asDouble(), location.path("x").asDouble())); }
            catch (IllegalArgumentException ignored) { /* tọa độ hỏng: bỏ qua ứng viên */ }
        }
        return Optional.empty();
    }

    static String normalize(String address) {
        if (address == null) return "";
        String value = address.strip().replaceAll("\\s+", " ");
        for (var rule : ABBREVIATIONS) value = rule.getKey().matcher(value).replaceAll(rule.getValue());
        value = value.replaceAll("\\s*,\\s*", ", ").replaceAll("(, )+", ", ").replaceAll("^, |, $", "");
        // Đã giới hạn tìm trong Việt Nam; để "Việt Nam" ở cuối thì bước kiểm tra tỉnh/thành sẽ hiểu nhầm là tên tỉnh.
        return value.replaceFirst("(?iu)(?:, )?(?<!\\p{L})(?:việt nam|viet nam|vietnam|vn)$", "").strip();
    }

    /** Địa chỉ đầy đủ trước, rồi bớt dần phần đầu (số nhà, đường...) nhưng luôn giữ ít nhất 2 cấp hành chính. */
    static List<String> queries(String normalized) {
        var parts = Arrays.stream(normalized.split(", ")).filter(part -> !part.isBlank()).toList();
        var result = new ArrayList<String>();
        result.add(String.join(", ", parts));
        for (int start = 1; parts.size() - start >= 2 && result.size() < 3; start++)
            result.add(String.join(", ", parts.subList(start, parts.size())));
        return result;
    }

    /** Tên tỉnh/thành ở cuối địa chỉ (không dấu, chữ thường); null nếu địa chỉ chỉ có một phần. */
    static String province(String normalized) {
        int comma = normalized.lastIndexOf(", ");
        if (comma < 0) return null;
        String last = fold(normalized.substring(comma + 2)).replaceFirst("^(tinh|thanh pho|tp) ", "").strip();
        return last.isEmpty() || last.chars().allMatch(Character::isDigit) ? null : last;
    }

    static String fold(String value) {
        String noMarks = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static AddressNotFoundException notFound() {
        return new AddressNotFoundException("Không xác định được vị trí");
    }
}
