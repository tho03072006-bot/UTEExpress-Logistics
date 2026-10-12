package vn.edu.hcmute.uteexpress.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class ShipmentWeightValidator implements ConstraintValidator<ShipmentWeight, Double> {
    @Override
    public boolean isValid(Double value, ConstraintValidatorContext context) {
        return value == null || isSupportedWeight(value);
    }

    public static boolean isSupportedWeight(double value) {
        return Double.isFinite(value) && value >= 0.1 && value <= 100
                && BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 3;
    }
}
