package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;

public record AccessRevocationRequest(
    @NotNull @PositiveOrZero Long revision,
    @NotBlank @Size(max = 72) String password,
    @NotBlank(message = "Nhập lý do thu hồi truy cập.")
    @Size(min = 5, max = 300, message = "Lý do từ 5–300 ký tự.")
    @Pattern(regexp = "^[^\\p{Cntrl}<>]*$", message = "Lý do không chứa ký tự điều khiển hoặc HTML.") String reason,
    Boolean confirmSelf) { }
