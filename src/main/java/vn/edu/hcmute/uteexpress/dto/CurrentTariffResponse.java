package vn.edu.hcmute.uteexpress.dto;

import java.math.BigDecimal;
import vn.edu.hcmute.uteexpress.entity.Order;

/** Giá đang hiệu lực công khai, không chứa dữ liệu quản trị hay giá đã hết hạn. */
public record CurrentTariffResponse(Order.ServiceType serviceType, BigDecimal baseFee, BigDecimal feePerKg) { }
