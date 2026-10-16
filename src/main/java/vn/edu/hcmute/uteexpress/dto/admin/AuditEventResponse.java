package vn.edu.hcmute.uteexpress.dto.admin;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.admin.AuditEvent;
public record AuditEventResponse(String actor, String action, String target, String detail, LocalDateTime createdAt) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getActor(), event.getAction(), event.getTarget(), event.getDetail(), event.getCreatedAt());
    }
}

