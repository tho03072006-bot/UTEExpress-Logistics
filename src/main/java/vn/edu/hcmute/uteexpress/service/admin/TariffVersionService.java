package vn.edu.hcmute.uteexpress.service.admin;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.admin.TariffScheduleRequest;
import vn.edu.hcmute.uteexpress.entity.admin.ShippingTariffVersion;
public interface TariffVersionService {
    Page<ShippingTariffVersion> list(String actor, int page);
    void schedule(String actor, TariffScheduleRequest form);
    void cancel(String actor, Long id, long revision, String reason);
}
