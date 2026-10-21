package vn.edu.hcmute.uteexpress.validation;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Quy tắc dùng chung cho đăng ký, quên/đổi mật khẩu và lớp service. */
public final class PublicAccountValidation {
    public static final String USERNAME = "[A-Za-z0-9][A-Za-z0-9_.-]{2,49}";
    public static final String PHONE = "0[35789][0-9]{8}";
    public static final String NAME = "(?=.*\\p{L})[\\p{L}\\p{M} .'-]+";
    public static final String PASSWORD_MESSAGE = "Mật khẩu từ 8–64 ký tự, có chữ và số, không có khoảng trắng; tối đa 72 byte UTF-8.";
    private PublicAccountValidation() { }
    public static String email(String value) { return value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
    public static String text(String value) { return value == null ? null : value.strip(); }
    public static String phone(String value) {
        if(value==null)return null;
        String normalized=value.strip().replace(" ","").replace("-","").replace(".","");
        return normalized.startsWith("+84")?"0"+normalized.substring(3):normalized;
    }
    public static boolean passwordValid(String value) {
        return value != null && value.length() >= 8 && value.length() <= 64
            && value.getBytes(StandardCharsets.UTF_8).length <= 72
            && value.codePoints().anyMatch(Character::isLetter)
            && value.chars().anyMatch(c -> c >= '0' && c <= '9')
            && value.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c));
    }
    public static void requirePassword(String value) {
        if (!passwordValid(value)) throw new IllegalArgumentException(PASSWORD_MESSAGE);
    }
    public static void requireEmail(String value) {
        if (value == null || value.length() > 100 || !value.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}"))
            throw new IllegalArgumentException("Email không hợp lệ hoặc dài quá 100 ký tự.");
        String local=value.substring(0,value.indexOf('@'));
        if(local.startsWith(".") || local.endsWith(".") || value.contains("..")) throw new IllegalArgumentException("Email không hợp lệ.");
        for(String label:value.substring(value.indexOf('@')+1).split("\\."))
            if(!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) throw new IllegalArgumentException("Tên miền email không hợp lệ.");
    }
    public static void requireRegistration(String username, String password, String email, String fullName) {
        if (username == null || !username.matches(USERNAME)) throw new IllegalArgumentException("Tên đăng nhập từ 3–50 ký tự, chỉ gồm chữ không dấu, số, dấu chấm/gạch ngang/gạch dưới.");
        requirePassword(password); requireEmail(email);
        if (fullName != null && (fullName.length() > 100 || fullName.codePoints().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Họ tên tối đa 100 ký tự và không chứa ký tự điều khiển.");
    }
}
