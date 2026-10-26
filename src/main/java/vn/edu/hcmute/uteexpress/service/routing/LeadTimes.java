package vn.edu.hcmute.uteexpress.service.routing;

import java.text.Normalizer;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Đọc số giờ tối đa từ "thời gian dự kiến" của biểu giá tuyến, để hạn giao khớp đúng điều đã hứa với khách.
 * Nhận các dạng "Trong 4 giờ", "Trong ngày", "2 ngày", "2–3 ngày" (mốc trên là hạn); tối đa 720 giờ như Chính sách vận hành.
 */
public final class LeadTimes {
    public static final int MAX_HOURS = 720;
    public static final String FORMAT_HINT = "\"Trong 4 giờ\", \"Trong ngày\", \"2 ngày\" hoặc \"2–3 ngày\"";
    private static final Pattern RANGE = Pattern.compile("(?:trong\\s+)?(\\d{1,3})(?:\\s*[–-]\\s*(\\d{1,3}))?\\s*(giờ|ngày)");

    private LeadTimes() { }

    public static OptionalInt hours(String text) {
        if (text == null) return OptionalInt.empty();
        String value = Normalizer.normalize(text, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (value.equals("trong ngày")) return OptionalInt.of(24);
        var match = RANGE.matcher(value);
        if (!match.matches()) return OptionalInt.empty();
        int low = Integer.parseInt(match.group(1));
        int high = match.group(2) == null ? low : Integer.parseInt(match.group(2));
        if (low == 0 || high < low) return OptionalInt.empty();
        int hours = match.group(3).equals("giờ") ? high : high * 24;
        return hours > MAX_HOURS ? OptionalInt.empty() : OptionalInt.of(hours);
    }
}
