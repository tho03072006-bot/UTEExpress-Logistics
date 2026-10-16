package vn.edu.hcmute.uteexpress.service.manager;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.manager.*;
public interface ManagerReportService {
    OperationsReportResponse report(String actor, LocalDate from, LocalDate to);
}

