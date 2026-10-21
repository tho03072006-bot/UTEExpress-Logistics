package vn.edu.hcmute.uteexpress.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;

/**
 * Giới hạn độ dài theo byte UTF-8 (BCrypt chỉ dùng 72 byte đầu và Spring Security từ chối mật khẩu dài hơn).
 * Gắn trực tiếp lên ô nhập để lỗi hiện ngay dưới ô đó; chữ có dấu tiếng Việt chiếm 2–3 byte.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
public @interface MaxUtf8Bytes {
    int value() default 72;
    String message() default "Mật khẩu quá dài (tối đa 72 byte; chữ có dấu tính 2–3 byte).";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {
        private int max;
        @Override public void initialize(MaxUtf8Bytes annotation) { max = annotation.value(); }
        @Override public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
