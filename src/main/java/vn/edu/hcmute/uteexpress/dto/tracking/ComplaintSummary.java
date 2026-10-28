package vn.edu.hcmute.uteexpress.dto.tracking;

import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.OrderComplaint;

public record ComplaintSummary(Long id, String trackingCode, String subject,
        OrderComplaint.Status status, LocalDateTime createdAt) { }
