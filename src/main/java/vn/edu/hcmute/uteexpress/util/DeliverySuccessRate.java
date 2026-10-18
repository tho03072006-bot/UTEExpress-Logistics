package vn.edu.hcmute.uteexpress.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** FAILED còn giao lại được: không phải kết quả cuối và không được tính vào mẫu số. */
public final class DeliverySuccessRate {
    private DeliverySuccessRate() { }

    public static BigDecimal calculate(long delivered, long returned) {
        if (delivered < 0 || returned < 0) throw new IllegalArgumentException("Số đơn không được âm.");
        BigDecimal completed = BigDecimal.valueOf(delivered).add(BigDecimal.valueOf(returned));
        return completed.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(delivered).multiply(BigDecimal.valueOf(100)).divide(completed, 1, RoundingMode.HALF_UP);
    }
}
