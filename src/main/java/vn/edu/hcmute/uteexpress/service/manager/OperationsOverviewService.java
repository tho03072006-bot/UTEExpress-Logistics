package vn.edu.hcmute.uteexpress.service.manager;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse;
public interface OperationsOverviewService {
    enum Alert { UNASSIGNED, LATE_PICKUP, LATE_DELIVERY, RETRY_DUE, MANY_FAILURES, PAYMENT_ISSUE }
    Page<Order> alerts(String actor, Alert alert, int page);
    Page<OrderComplaint> lateComplaints(String actor, int page);
    Page<CodSettlement> lateCash(String actor, int page);
    Page<ShipperPerformanceResponse> performance(String actor, int page);
}
