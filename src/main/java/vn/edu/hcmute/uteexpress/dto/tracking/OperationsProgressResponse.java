package vn.edu.hcmute.uteexpress.dto.tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;

/** Bản đọc cho người liên quan: không lộ người duyệt, lý do nội bộ, ảnh hay thông tin chứng từ. */
public record OperationsProgressResponse(OperationsCase.Kind kind, OperationsCase.Status status,
        BigDecimal amount, LocalDateTime nextAttemptAt, LocalDateTime updatedAt) { }
