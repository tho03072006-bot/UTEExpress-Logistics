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

    @Test
    void contextLoads() {
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
