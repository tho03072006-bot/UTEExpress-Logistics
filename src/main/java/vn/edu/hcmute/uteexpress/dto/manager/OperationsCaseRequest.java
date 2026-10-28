package vn.edu.hcmute.uteexpress.dto.manager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
public record OperationsCaseRequest(@NotNull OperationsCase.Kind kind, @NotNull @Positive Long targetId,
        @NotNull @Min(0) Long revision,
        @DecimalMin("1") @DecimalMax("999999999999") @Digits(integer = 12, fraction = 0) BigDecimal amount,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime nextAttemptAt,
        @NotBlank @Size(min = 5, max = 500) String reason,
        @Size(max = 100) String receiptReference,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestToken) { }
