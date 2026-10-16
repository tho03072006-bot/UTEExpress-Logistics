package vn.edu.hcmute.uteexpress.dto.manager;
import jakarta.validation.constraints.*;
public record PromotionStatusRequest(@NotNull(message = "Thiếu phiên bản khuyến mãi.")
    @PositiveOrZero(message = "Phiên bản không được âm.") Long revision,
    @NotNull(message = "Thiếu trạng thái khuyến mãi.") Boolean active) { }
