package vn.edu.hcmute.uteexpress.service.admin.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.hcmute.uteexpress.entity.admin.AuditEvent;
import vn.edu.hcmute.uteexpress.repository.admin.AuditEventRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;

@Service
public class AuditServiceImpl implements AuditService {
    private final AuditEventRepository events;
    private final StaffAccessPolicy access;
    private final Clock clock;
    public AuditServiceImpl(AuditEventRepository events, StaffAccessPolicy access, Clock clock) {
        this.events = events; this.access = access; this.clock = clock;
    }
    // Bắt buộc chung giao dịch: không ghi nhật ký thành công nếu thao tác bị rollback.
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void record(String actor, String action, String target, String detail) {
        var event = new AuditEvent();
        event.setActor(safe(actor, 50)); event.setAction(safe(action, 50));
        event.setTarget(safe(target, 80)); event.setDetail(safe(detail, 500));
        event.setCreatedAt(LocalDateTime.now(clock));
        events.save(event);
    }
    @Override @Transactional(readOnly = true)
    public Page<vn.edu.hcmute.uteexpress.dto.admin.AuditEventResponse> findEvents(String actor, int page) {
        access.requireAdmin(actor); access.validatePage(page);
        return events.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, 20))
            .map(vn.edu.hcmute.uteexpress.dto.admin.AuditEventResponse::from);
    }
    private String safe(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Dữ liệu nhật ký không hợp lệ.");
        }
        return value.strip();
    }
}

