package vn.edu.hcmute.uteexpress.service.impl;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.entity.PromoCode;
import vn.edu.hcmute.uteexpress.repository.PromoCodeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromoCodeQuotaTest {
    private final PromoCodeRepository repository = mock(PromoCodeRepository.class);
    private final PromoCodeServiceImpl service = new PromoCodeServiceImpl(repository);

    @Test void quotaIncrementReReadsLockedRowInsteadOfUsingDetachedCount() {
        PromoCode stale = promo(0);
        PromoCode latest = promo(9);
        when(repository.findByCodeIgnoreCaseForUpdate("SAVE10")).thenReturn(Optional.of(latest));
        service.markUsed(stale);
        assertThat(latest.getUsedCount()).isEqualTo(10);
        assertThat(stale.getUsedCount()).isZero();
        verify(repository).save(latest);

        assertThatThrownBy(() -> service.markUsed(stale)).hasMessageContaining("hết lượt");
        verify(repository, times(1)).save(any());
    }

    @Test void checksEligibilityUnderTheSameLockUsedToReserveUsage() {
        PromoCode latest = promo(9);
        latest.setMinOrderAmount(BigDecimal.valueOf(20000));
        when(repository.findByCodeIgnoreCaseForUpdate("save10")).thenReturn(Optional.of(latest));
        assertThatThrownBy(() -> service.requireUsableCode(" save10 ", BigDecimal.valueOf(18000)))
                .hasMessageContaining("20.000");
        verify(repository, never()).findByCodeIgnoreCase(any());
        verify(repository, never()).save(any());
    }

    @Test void discountCannotExceedTheFeeAndUsesIntegerVnd() {
        PromoCode promo = promo(0);
        promo.setDiscountValue(BigDecimal.valueOf(7));
        assertThat(service.calculateDiscount(promo, BigDecimal.valueOf(15001)))
                .isEqualByComparingTo("1050").hasScaleOf(0);
        promo.setDiscountType(PromoCode.DiscountType.FIXED_AMOUNT);
        promo.setDiscountValue(BigDecimal.valueOf(20000));
        assertThat(service.calculateDiscount(promo, BigDecimal.valueOf(15001)))
                .isEqualByComparingTo("15001").hasScaleOf(0);
    }

    private static PromoCode promo(int usedCount) {
        PromoCode promo = new PromoCode();
        promo.setCode("SAVE10");
        promo.setUsedCount(usedCount);
        promo.setUsageLimit(10);
        promo.setDiscountValue(BigDecimal.valueOf(10));
        return promo;
    }
}
