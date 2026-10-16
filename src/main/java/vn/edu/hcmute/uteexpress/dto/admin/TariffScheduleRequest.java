package vn.edu.hcmute.uteexpress.dto.admin;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.hcmute.uteexpress.entity.Order;
public record TariffScheduleRequest(@NotNull Order.ServiceType serviceType,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer = 9, fraction = 0) BigDecimal baseFee,
        @NotNull @DecimalMin("0") @DecimalMax("999999999") @Digits(integer = 9, fraction = 0) BigDecimal feePerKg,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
        @NotBlank @Size(min = 5, max = 500) String reason) { }
