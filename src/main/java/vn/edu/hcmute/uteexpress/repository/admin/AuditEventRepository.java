package vn.edu.hcmute.uteexpress.repository.admin;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.admin.AuditEvent;
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    long countByActionAndDetailStartingWithAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String action, String prefix, java.time.LocalDateTime from, java.time.LocalDateTime to);
    Page<AuditEvent> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
    @org.springframework.data.jpa.repository.Query("""
        select e from AuditEvent e where (:operator = '' or e.actor = :operator)
        and (:category = 'ALL'
          or (:category = 'ACCOUNT' and (e.action like 'ACCOUNT_%' or e.action like 'STAFF_%'))
          or (:category = 'OPERATIONS' and (e.action like 'CASE_%' or e.action like 'TRIP_%'
              or e.action in ('ORDER_ASSIGN','RETURN_START','RETRY_START','COUNTER_RECEIVE','BRANCH_UPDATE')))
          or (:category = 'PRICING' and (e.action like 'TARIFF_%' or e.action in ('POLICY_UPDATE','ROUTE_TARIFF_UPDATE')))
          or (:category = 'PROMOTION' and e.action like 'PROMOTION_%')
          or (:category = 'SECURITY' and e.action in ('API_TOKEN_ISSUE','ACCESS_REVOKE','STAFF_PASSWORD_RESET','STAFF_PASSWORD_CHANGE','ACCOUNT_PASSWORD_RESET')))
        and (:start is null or e.createdAt >= :start) and (:end is null or e.createdAt < :end)
        """)
    Page<AuditEvent> findFiltered(@org.springframework.data.repository.query.Param("operator") String operator,
        @org.springframework.data.repository.query.Param("category") String category,
        @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
        @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end, Pageable pageable);
}
