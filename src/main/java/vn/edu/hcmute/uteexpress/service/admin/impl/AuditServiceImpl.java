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
    @Override @Transactional(readOnly = true)
    public Page<vn.edu.hcmute.uteexpress.dto.admin.AuditEventResponse> findEvents(String actor,
            vn.edu.hcmute.uteexpress.dto.admin.AuditFilterRequest filter, int page) {
        access.requireAdmin(actor); access.validatePage(page); access.validate(filter);
        java.time.LocalDate start = filter.getFrom(), end = filter.getTo(), today = java.time.LocalDate.now(clock);
        for (var date : new java.time.LocalDate[]{start,end}) {
            if (date != null && (date.getYear() < 2000 || date.isAfter(today)))
                throw new IllegalArgumentException("Ngày lọc từ năm 2000 và không được ở tương lai.");
        }
        if (start != null || end != null) {
            end = end == null ? today : end; start = start == null ? end.minusDays(29) : start;
            if (filter.getFrom() == null && start.getYear() < 2000) start = java.time.LocalDate.of(2000,1,1);
            if (start.getYear() < 2000 || start.isAfter(end) || end.isAfter(today)
                    || java.time.temporal.ChronoUnit.DAYS.between(start, end) >= 366)
                throw new IllegalArgumentException("Khoảng ngày từ năm 2000, không ở tương lai, từ ngày không sau đến ngày và tối đa 366 ngày.");
        }
        return events.findFiltered(filter.getOperator(), filter.getCategory().name(),
            start == null ? null : start.atStartOfDay(), end == null ? null : end.plusDays(1).atStartOfDay(),
            PageRequest.of(page, 20, Sort.by("createdAt").descending().and(Sort.by("id").descending())))
            .map(vn.edu.hcmute.uteexpress.dto.admin.AuditEventResponse::from);
    }
    private String safe(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Dữ liệu nhật ký không hợp lệ.");
        }
        return value.strip();
    }
}

