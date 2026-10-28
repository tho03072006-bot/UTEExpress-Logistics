package vn.edu.hcmute.uteexpress.dto.tracking;

import java.math.BigDecimal;

/** Tổng số đơn và số tiền COD; sum() trả null khi không có dòng nên quy về 0. */
public record CodTotals(Long count, BigDecimal amount) {
    public CodTotals {
        count = count == null ? 0L : count;
        amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
