package vn.edu.hcmute.uteexpress.controller.manager;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.hcmute.uteexpress.entity.routing.Branch;
import static org.junit.jupiter.api.Assertions.*;

/** Bước 15: CSV báo cáo ghi tên chi nhánh đã lọc thay vì chỉ mã số. */
class ManagerReportCsvTest {
    static Branch branch(long id, String code, String name) {
        var branch = new Branch(code, name, "79", "Kho kiểm thử", code + "-HUB", LocalDateTime.of(2026, 10, 24, 9, 0));
        ReflectionTestUtils.setField(branch, "id", id);
        return branch;
    }

    @Test void labelNamesTheFilteredBranch() {
        var sgn = branch(1, "SGN", "Chi nhánh TP. Hồ Chí Minh"); var han = branch(2, "HAN", "Chi nhánh Hà Nội");
        assertEquals("Chi nhánh Hà Nội (HAN)", ManagerReportController.branchLabel(List.of(sgn, han), 2L), "Admin lọc một chi nhánh");
        assertEquals("Chi nhánh TP. Hồ Chí Minh (SGN)", ManagerReportController.branchLabel(List.of(sgn), null), "Manager luôn là chi nhánh của mình");
        assertEquals("Tất cả chi nhánh", ManagerReportController.branchLabel(List.of(sgn, han), null), "Admin không lọc");
    }

    @Test void cellIsSafeForExcel() {
        assertEquals("\"Chi nhánh A, B\"", ManagerReportController.csvCell("Chi nhánh A, B"));
        assertEquals("'=HYPERLINK(1)", ManagerReportController.csvCell("=HYPERLINK(1)"));
        assertEquals("Dòng 1 Dòng 2", ManagerReportController.csvCell("Dòng 1\r\nDòng 2"));
    }
}
