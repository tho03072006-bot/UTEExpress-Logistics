package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
import vn.edu.hcmute.uteexpress.repository.CodSettlementRepository;
import vn.edu.hcmute.uteexpress.repository.admin.OperationsCaseRepository;
import vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService;
import vn.edu.hcmute.uteexpress.service.tracking.*;

@Service
@Transactional(readOnly = true)
public class OperationsProgressServiceImpl implements OperationsProgressService {
    private final OperationsCaseRepository cases;
    private final CodSettlementRepository settlements;
    private final LogisticsAccess access;
    private final OrderJourneyService journey;
    private final OperationsCaseService operations;

    public OperationsProgressServiceImpl(OperationsCaseRepository cases, CodSettlementRepository settlements,
            LogisticsAccess access, OrderJourneyService journey, OperationsCaseService operations) {
        this.cases = cases; this.settlements = settlements; this.access = access;
        this.journey = journey; this.operations = operations;
    }

    @Override
    public Page<OperationsProgressResponse> findOrderProgress(Long orderId, String username, int page) {
        validateId(orderId);
        var request = pageRequest(page);
        journey.accessibleOrder(orderId, username);
        return cases.findOrderProgress(orderId, request);
    }

    @Override
    public Page<OperationsProgressResponse> findSettlementProgress(Long settlementId, String username, int page) {
        var request = pageRequest(page);
        var settlement = ownSettlement(settlementId, username);
        return cases.findSettlementProgress(settlement.getId(), request);
    }

    @Override
    public CashBalanceResponse findSettlementBalance(Long settlementId, String username) {
        var settlement = ownSettlement(settlementId, username);
        BigDecimal refundDue = BigDecimal.ZERO;
        if (settlement.getStatus() == CodSettlement.Status.CONFIRMED) {
            // Đã duyệt hoàn chưa có nghĩa đã chi tiền. Chỉ trừ khi có biên nhận hoàn tất.
            refundDue = settlement.getDeclaredAmount().subtract(settlement.getTotalAmount())
                    .subtract(cases.sumAmounts(settlement, OperationsCase.Kind.CASH_REFUND,
                            List.of(OperationsCase.Status.COMPLETED))).max(BigDecimal.ZERO);
        }
        return new CashBalanceResponse(operations.outstanding(settlement), refundDue);
    }

    @Override
    public CashBalanceResponse findShipperBalance(String username) {
        var shipper = access.shipper(username);
        var shortage = settlements.sumConfirmedShortage(shipper).subtract(cases.sumAcceptedByShipper(shipper,
                OperationsCase.Kind.CASH_SUPPLEMENT, List.of(OperationsCase.Status.APPROVED, OperationsCase.Status.COMPLETED)));
        var excess = settlements.sumConfirmedExcess(shipper).subtract(cases.sumAcceptedByShipper(shipper,
                OperationsCase.Kind.CASH_REFUND, List.of(OperationsCase.Status.COMPLETED)));
        return new CashBalanceResponse(shortage.max(BigDecimal.ZERO), excess.max(BigDecimal.ZERO));
    }

    private CodSettlement ownSettlement(Long id, String username) {
        validateId(id);
        var shipper = access.shipper(username);
        return settlements.findById(id).filter(s -> s.getShipper().getId().equals(shipper.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nộp tiền."));
    }

    private PageRequest pageRequest(int page) {
        return PageRequest.of(LogisticsInput.page(page), 20, Sort.by("updatedAt", "id").descending());
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Mã hồ sơ không hợp lệ.");
    }
}
