package vn.edu.hcmute.uteexpress.dto.tracking;

import java.math.BigDecimal;

public record CashBalanceResponse(BigDecimal outstanding, BigDecimal refundDue) { }
