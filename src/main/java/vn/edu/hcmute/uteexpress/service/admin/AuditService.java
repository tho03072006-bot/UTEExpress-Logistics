package vn.edu.hcmute.uteexpress.service.admin;

import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.entity.admin.AuditEvent;

public interface AuditService {
    void record(String actor, String action, String target, String detail);
    Page<vn.edu.hcmute.uteexpress.dto.admin.AuditEventResponse> findEvents(String actor, int page);
    Page<vn.edu.hcmute.uteexpress.dto.admin.AuditEventResponse> findEvents(String actor,
            vn.edu.hcmute.uteexpress.dto.admin.AuditFilterRequest filter, int page);
}

