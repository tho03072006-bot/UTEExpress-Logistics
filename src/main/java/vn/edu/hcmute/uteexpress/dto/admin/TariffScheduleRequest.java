package vn.edu.hcmute.uteexpress.dto.admin;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.hcmute.uteexpress.entity.Order;
public record TariffScheduleRequest(@NotNull Order.ServiceType serviceType,
        @NotNull @DecimalMin("0") @DecimalMax(value = "100000000", message = "Phí cơ bản tối đa 100 triệu đồng.") @Digits(integer = 9, fraction = 0) BigDecimal baseFee,
        @NotNull @DecimalMin("0") @DecimalMax(value = "5000000", message = "Phí mỗi kg tối đa 5 triệu đồng.") @Digits(integer = 7, fraction = 0) BigDecimal feePerKg,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
        @NotBlank @Size(min = 5, max = 500) String reason) { }
