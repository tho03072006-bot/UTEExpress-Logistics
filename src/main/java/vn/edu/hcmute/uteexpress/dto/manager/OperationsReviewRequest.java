package vn.edu.hcmute.uteexpress.dto.manager;
import jakarta.validation.constraints.*;
public record OperationsReviewRequest(@NotNull @Min(0) Long revision, @NotNull Boolean approve,
        @NotBlank @Size(min = 5, max = 500) String reason) { }
