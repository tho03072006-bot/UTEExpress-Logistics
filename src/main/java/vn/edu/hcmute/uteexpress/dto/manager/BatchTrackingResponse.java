package vn.edu.hcmute.uteexpress.dto.manager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Không xuất tên, số điện thoại, địa chỉ hoặc bằng chứng giao hàng ra tệp. */
public record BatchTrackingResponse(String trackingCode, boolean found, String status,
        String serviceType, BigDecimal shippingFee, LocalDateTime createdAt, boolean assigned) { }
