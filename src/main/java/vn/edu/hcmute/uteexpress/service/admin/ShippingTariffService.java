package vn.edu.hcmute.uteexpress.service.admin;

import java.math.BigDecimal;
import java.util.List;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.ShippingTariff;
import vn.edu.hcmute.uteexpress.dto.admin.ShippingTariffUpdateRequest;

public interface ShippingTariffService {
    BigDecimal quote(Order.ServiceType serviceType, double weightKg);
    List<vn.edu.hcmute.uteexpress.dto.CurrentTariffResponse> currentTariffs();
    List<vn.edu.hcmute.uteexpress.dto.admin.ShippingTariffResponse> findTariffs(String actor);
    void updateTariff(String actor, Order.ServiceType serviceType, ShippingTariffUpdateRequest request);
}

