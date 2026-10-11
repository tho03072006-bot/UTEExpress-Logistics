package vn.edu.hcmute.uteexpress.service.tracking;

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
}
