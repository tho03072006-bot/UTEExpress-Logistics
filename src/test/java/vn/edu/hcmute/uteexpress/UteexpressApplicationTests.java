package vn.edu.hcmute.uteexpress;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test mac dinh cua Spring Boot: chi kiem tra context khoi dong duoc.
 * Cần cấu hình Aiven MySQL thật. Chỉ kiểm tra khởi động và SELECT;
 * không seed, migrate hoặc thay đổi dữ liệu nghiệp vụ của nhóm.
 */
@SpringBootTest
class UteexpressApplicationTests {
    @org.springframework.beans.factory.annotation.Autowired
    vn.edu.hcmute.uteexpress.repository.admin.AuditEventRepository auditEvents;

    @org.springframework.beans.factory.annotation.Autowired
    vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderStatisticsRepository orderStatistics;

    @org.springframework.beans.factory.annotation.Autowired
    vn.edu.hcmute.uteexpress.repository.manager.OperationsOverviewRepository overview;

    @org.springframework.beans.factory.annotation.Autowired
    vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes;

    @org.springframework.beans.factory.annotation.Autowired
    vn.edu.hcmute.uteexpress.repository.admin.ComplaintActivityRepository complaintActivities;

    @Test
    void contextLoads() {
    }

    @Test @org.springframework.transaction.annotation.Transactional(readOnly = true)
    void complaintConversationPagingQueriesExecuteOnAivenWithoutWritingData() {
        org.junit.jupiter.api.Assertions.assertEquals(0L, complaintActivities.countByComplaintId(-1L));
        org.junit.jupiter.api.Assertions.assertTrue(complaintActivities.findByComplaintIdOrderByCreatedAtAscIdAsc(-1L,
                org.springframework.data.domain.PageRequest.of(0, 50)).isEmpty());
    }

    @Test @org.springframework.transaction.annotation.Transactional(readOnly = true)
    void paymentAlertQueryExecutesOnAivenWithoutWritingData() {
        var now=java.time.LocalDateTime.now();
        var result=overview.alerts("PAYMENT_ISSUE",null,now.minusHours(24),now,3,org.springframework.data.domain.PageRequest.of(0,1));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->overview.deliveryCandidates(now.minusHours(4),null));
        org.junit.jupiter.api.Assertions.assertTrue(result.getTotalElements()>=0);
        var ids=result.isEmpty()?java.util.List.of(-1L):result.map(vn.edu.hcmute.uteexpress.entity.Order::getId).getContent();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->routes.idsAtDeliveryHub(ids,null));
    }

    @Test @org.springframework.transaction.annotation.Transactional(readOnly = true)
    void dashboardAggregatesExecuteOnAivenWithoutWritingData() {
        var today = java.time.LocalDate.now();
        var statuses = orderStatistics.countGroupedStatuses();
        var days = orderStatistics.countCreatedByDay(today.minusDays(13).atStartOfDay(),today.plusDays(1).atStartOfDay());
        org.junit.jupiter.api.Assertions.assertTrue(statuses.stream().allMatch(row -> row.getTotal() >= 0));
        org.junit.jupiter.api.Assertions.assertTrue(days.stream().allMatch(row -> row.getTotal() >= 0));
    }

    @Test @org.springframework.transaction.annotation.Transactional(readOnly = true)
    void auditFiltersExecuteOnAivenWithoutWritingData() {
        for (var category : vn.edu.hcmute.uteexpress.dto.admin.AuditFilterRequest.Category.values()) {
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> auditEvents.findFiltered("",category.name(),
                java.time.LocalDate.now().minusDays(1).atStartOfDay(), java.time.LocalDate.now().plusDays(1).atStartOfDay(),
                org.springframework.data.domain.PageRequest.of(0,1)).getTotalElements());
        }
    }

}
