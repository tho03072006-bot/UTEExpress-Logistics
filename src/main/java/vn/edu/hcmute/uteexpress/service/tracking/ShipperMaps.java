package vn.edu.hcmute.uteexpress.service.tracking;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Đường dẫn chỉ đường Google Maps dùng chung cho mọi nút "Chỉ đường" của Shipper.
 * <ul>
 *   <li>{@code travelmode=driving}: chỉ hiện đường bộ, không còn máy bay, xe khách, tàu hỏa. Google chưa hỗ trợ
 *   chế độ xe máy ({@code two-wheeler}) ở Việt Nam nên dùng chế độ ô tô.</li>
 *   <li>{@code dir_action=navigate}: trên điện thoại mở thẳng dẫn đường từ vị trí hiện tại của Shipper.</li>
 *   <li>Địa chỉ luôn kèm ", Việt Nam" để Google không chọn nhầm nơi trùng tên ở nước khác.</li>
 * </ul>
 * Trong template: {@code th:href="${@maps.to(order.receiverAddress)}"}.
 */
@Component("maps")
public class ShipperMaps {
    static final String DIRECTIONS = "https://www.google.com/maps/dir/?api=1&travelmode=driving";

    public String to(String address) { return directions(address); }

    public static String directions(String address) {
        return DIRECTIONS + "&dir_action=navigate&destination=" + URLEncoder.encode(place(address), StandardCharsets.UTF_8);
    }

    /** Bỏ ký tự điều khiển và dấu "|" (phân cách điểm của Google Maps), gộp khoảng trắng, thêm quốc gia nếu thiếu. */
    public static String place(String address) {
        String value = address == null ? "" : address.replaceAll("[\\p{Cntrl}|]+", " ").replaceAll("\\s+", " ").strip();
        if (value.isEmpty()) return value;
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return plain.contains("viet nam") || plain.contains("vietnam") ? value : value + ", Việt Nam";
    }
}
