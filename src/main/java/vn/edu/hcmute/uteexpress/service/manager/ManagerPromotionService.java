package vn.edu.hcmute.uteexpress.service.manager;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.manager.*;
public interface ManagerPromotionService {
    Page<PromotionResponse> findPromotions(String actor, int page);
    PromotionRequest preparePromotion(String actor, Long id);
    void savePromotion(String actor, Long id, PromotionRequest request);
    void changePromotionStatus(String actor, Long id, PromotionStatusRequest request);
}

