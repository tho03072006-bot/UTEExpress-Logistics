package vn.edu.hcmute.uteexpress.dto.admin;
import jakarta.validation.constraints.*;
public record OperationsPolicyRequest(@NotNull @Min(0) Long revision,
        @NotNull @Min(1) @Max(168) Integer pickupHours,
        @NotNull @Min(1) @Max(720) Integer standardHours,
        @NotNull @Min(1) @Max(720) Integer expressHours,
        @NotNull @Min(1) @Max(720) Integer superExpressHours,
        @NotNull @Min(1) @Max(10) Integer maxDeliveryAttempts,
        @NotNull @Min(1) @Max(168) Integer complaintResponseHours,
        @NotNull @Min(1) @Max(2160) Integer complaintResolveHours,
        @NotBlank @Size(min = 5, max = 500) String reason) { }
