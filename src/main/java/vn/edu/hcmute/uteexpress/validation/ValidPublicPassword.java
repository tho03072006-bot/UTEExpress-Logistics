package vn.edu.hcmute.uteexpress.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Documented @Constraint(validatedBy = ValidPublicPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER}) @Retention(RetentionPolicy.RUNTIME)
public @interface ValidPublicPassword {
    String message() default PublicAccountValidation.PASSWORD_MESSAGE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Validator implements ConstraintValidator<ValidPublicPassword, String> {
        @Override public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || PublicAccountValidation.passwordValid(value);
        }
    }
}
