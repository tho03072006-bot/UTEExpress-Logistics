package vn.edu.hcmute.uteexpress.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Shared normalization and service-side checks, including callers outside MVC. */
public final class AuthInputRules {
    public static final String USERNAME_PATTERN = "[A-Za-z0-9._-]{3,50}";
    public static final String PHONE_PATTERN = "0[0-9]{9,10}";

    private AuthInputRules() { }

    public static String trim(String value) {
        return value == null ? null : value.strip();
    }

    public static String normalizeEmail(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    public static String username(String value) {
        String normalized = trim(value);
        if (normalized == null || !normalized.matches(USERNAME_PATTERN)) {
            throw new IllegalStateException("Tên đăng nhập cần 3–50 ký tự chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        }
        return normalized;
    }

    public static String email(String value) {
        String normalized = normalizeEmail(value);
        if (normalized == null || normalized.length() > 100
                || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("Email không hợp lệ hoặc vượt quá 100 ký tự.");
        }
        return normalized;
    }

    public static String text(String value, int maximum, String label, boolean required) {
        String normalized = trim(value);
        if (normalized == null || normalized.isEmpty()) {
            if (required) throw new IllegalStateException("Vui lòng nhập " + label + ".");
            return null;
        }
        if (normalized.length() > maximum || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException(label + " không hợp lệ hoặc vượt quá " + maximum + " ký tự.");
        }
        return normalized;
    }

    public static String phone(String value) {
        String normalized = trim(value);
        if (normalized == null || !normalized.matches(PHONE_PATTERN)) {
            throw new IllegalStateException("Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số.");
        }
        return normalized;
    }

    public static boolean passwordWithinByteLimit(String value) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public static void password(String value, boolean isNew) {
        if (value == null || value.isBlank() || (isNew && value.length() < 6)
                || value.length() > 72 || !passwordWithinByteLimit(value)) {
            throw new IllegalStateException("Mật khẩu cần ít nhất 6 ký tự và tối đa 72 byte UTF-8.");
        }
    }
}
