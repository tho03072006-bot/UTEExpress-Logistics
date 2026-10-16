package vn.edu.hcmute.uteexpress.service.tracking;

import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.tracking.*;

public interface OperationsProgressService {
    Page<OperationsProgressResponse> findOrderProgress(Long orderId, String username, int page);
    Page<OperationsProgressResponse> findSettlementProgress(Long settlementId, String username, int page);
    CashBalanceResponse findSettlementBalance(Long settlementId, String username);
    CashBalanceResponse findShipperBalance(String username);
}
