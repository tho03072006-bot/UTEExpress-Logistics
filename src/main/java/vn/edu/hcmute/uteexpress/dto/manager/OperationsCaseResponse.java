package vn.edu.hcmute.uteexpress.dto.manager;

import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;

/** Danh sách không tải ảnh chứng từ BLOB của từng hồ sơ. */
public record OperationsCaseResponse(Long id, OperationsCase.Kind kind, OperationsCase.Status status,
        String createdBy, LocalDateTime createdAt) { }
