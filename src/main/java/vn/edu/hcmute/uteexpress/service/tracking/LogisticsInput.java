package vn.edu.hcmute.uteexpress.service.tracking;

import java.time.LocalDate;

public final class LogisticsInput {
    private LogisticsInput() { }
    public static String text(String value, int min, int max, String label) {
        String result = value == null ? "" : value.strip();
        if (result.length() < min || result.length() > max
                || result.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new IllegalArgumentException(label + " phải có " + min + "–" + max + " ký tự hợp lệ.");
        }
        return result;
    }
    public static int page(int page) {
        if (page < 0 || page > 100_000) throw new IllegalArgumentException("Số trang không hợp lệ.");
        return page;
    }
    /**
     * Kiểm tra bộ lọc danh sách đơn của Shipper (từ khóa, khoảng ngày, số trang).
     * Trả về từ khóa đã escape cho LIKE với ký tự '!', hoặc null khi không lọc theo từ khóa.
     */
    public static String workFilter(String keyword, LocalDate from, LocalDate to, int page) {
        String term = text(keyword, 0, 100, "Từ khóa");
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("Từ ngày không được sau đến ngày.");
        for (var date : new LocalDate[]{from, to}) if (date != null && (date.getYear() < 2000 || date.getYear() > 2100))
            throw new IllegalArgumentException("Ngày lọc phải nằm trong năm 2000–2100.");
        page(page);
        return term.isEmpty() ? null : term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
