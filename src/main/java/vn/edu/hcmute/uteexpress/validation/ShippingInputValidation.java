package vn.edu.hcmute.uteexpress.validation;

/** Quy tắc dùng chung cho báo giá, tạo đơn và giỏ đơn. */
public final class ShippingInputValidation {
    private ShippingInputValidation() { }
    public static final String MIN_WEIGHT = "0.01";
    public static final String MAX_WEIGHT = "100";
    public static final String ADDRESS = "[^\\p{Cntrl}]+";

    public static boolean validWeight(double weightKg) {
        return Double.isFinite(weightKg) && weightKg >= 0.01 && weightKg <= 100;
    }
    public static void requireWeight(double weightKg) {
        if (!validWeight(weightKg)) throw new IllegalArgumentException("Khối lượng phải từ 0,01 đến 100 kg.");
    }
    public static void requireAddress(String address) {
        if (address == null || address.strip().length() < 5 || address.strip().length() > 255
                || !address.matches(ADDRESS))
            throw new IllegalArgumentException("Địa chỉ phải từ 5–255 ký tự và không chứa ký tự điều khiển.");
    }
}
