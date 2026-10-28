package vn.edu.hcmute.uteexpress.service.manager;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.manager.*;
public interface ManagerDispatchService {
    Page<DispatchOrderResponse> findPendingOrders(String actor, int page);
    Page<ShipperWorkloadResponse> findShippers(String actor, int page);
    void assignOrder(String actor, Long orderId, OrderAssignmentRequest request);
}

