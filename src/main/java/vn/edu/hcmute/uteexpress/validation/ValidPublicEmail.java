package vn.edu.hcmute.uteexpress.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Documented @Constraint(validatedBy=ValidPublicEmail.Validator.class)
@Target({ElementType.FIELD,ElementType.PARAMETER}) @Retention(RetentionPolicy.RUNTIME)
public @interface ValidPublicEmail {
    String message() default "Email cần tên hộp thư và tên miền hợp lệ, tối đa 100 ký tự.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Validator implements ConstraintValidator<ValidPublicEmail,String> {
        public boolean isValid(String value,ConstraintValidatorContext context) {
            if(value==null || value.isBlank())return true; // @NotBlank báo lỗi rỗng, tránh hai thông báo trùng nhau.
            try{PublicAccountValidation.requireEmail(value);return true;}catch(IllegalArgumentException ex){return false;}
        }
    }
}
