package vn.edu.hcmute.uteexpress.dto.admin;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.ShippingTariff;
public record ShippingTariffResponse(Order.ServiceType serviceType, BigDecimal baseFee, BigDecimal feePerKg,
        LocalDateTime updatedAt, long revision) {
    public static ShippingTariffResponse from(ShippingTariff tariff) {
        return new ShippingTariffResponse(tariff.getServiceType(), tariff.getBaseFee(), tariff.getFeePerKg(), tariff.getUpdatedAt(), tariff.getRevision());
    }
    public Order.ServiceType getServiceType() { return serviceType; }
    public BigDecimal getBaseFee() { return baseFee; }
    public BigDecimal getFeePerKg() { return feePerKg; }
    public long getRevision() { return revision; }
}

