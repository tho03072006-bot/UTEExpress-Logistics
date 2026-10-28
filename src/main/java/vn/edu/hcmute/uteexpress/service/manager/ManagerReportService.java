package vn.edu.hcmute.uteexpress.service.manager;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.manager.*;
public interface ManagerReportService {
    /** Manager: đơn gửi đi hoặc giao tới chi nhánh mình; Admin: mọi chi nhánh (branchId null) hoặc một chi nhánh. */
    OperationsReportResponse report(String actor, LocalDate from, LocalDate to, Long branchId);
    default OperationsReportResponse report(String actor, LocalDate from, LocalDate to) { return report(actor, from, to, null); }
    java.util.List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor);
}

