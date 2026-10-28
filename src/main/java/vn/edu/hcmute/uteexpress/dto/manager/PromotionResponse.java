package vn.edu.hcmute.uteexpress.dto.manager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.PromoCode;
public record PromotionResponse(Long id, String code, String description, PromoCode.DiscountType discountType,
        BigDecimal discountValue, Integer usageLimit, int usedCount, LocalDateTime startAt, LocalDateTime endAt,
        boolean active, long revision) {
    public static PromotionResponse from(PromoCode promo) {
        return new PromotionResponse(promo.getId(), promo.getCode(), promo.getDescription(), promo.getDiscountType(),
            promo.getDiscountValue(), promo.getUsageLimit(), promo.getUsedCount(), promo.getStartAt(), promo.getEndAt(),
            promo.isActive(), promo.getRevision());
    }
}

