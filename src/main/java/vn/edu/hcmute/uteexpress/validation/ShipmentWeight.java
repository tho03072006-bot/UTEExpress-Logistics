package vn.edu.hcmute.uteexpress.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The same finite weight policy is used by quote, direct order and draft forms. */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ShipmentWeightValidator.class)
public @interface ShipmentWeight {
    String message() default "Khối lượng phải từ 0,1 đến 100 kg, tối đa 3 chữ số thập phân";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
