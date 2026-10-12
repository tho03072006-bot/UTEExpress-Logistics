package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.PromoCode;
import vn.edu.hcmute.uteexpress.repository.PromoCodeRepository;
import vn.edu.hcmute.uteexpress.service.PromoCodeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class PromoCodeServiceImpl implements PromoCodeService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeServiceImpl(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    @Override
    public PromoCode requireUsableCode(String code, BigDecimal orderFee) {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Vui lòng nhập mã giảm giá.");
        }

        if (code.strip().length() > 30) {
            throw new IllegalStateException("Mã giảm giá tối đa 30 ký tự.");
        }
        // This lock remains held by the outer create-order transaction until payment and usage commit.
        PromoCode promoCode = promoCodeRepository.findByCodeIgnoreCaseForUpdate(code.strip())
                .orElseThrow(() -> new IllegalStateException(
                        "Mã \"" + code.trim() + "\" không tồn tại. Vui lòng kiểm tra lại."));

        requireAvailable(promoCode);

        BigDecimal fee = orderFee != null ? orderFee : BigDecimal.ZERO;
        if (promoCode.getMinOrderAmount() != null && fee.compareTo(promoCode.getMinOrderAmount()) < 0) {
            throw new IllegalStateException("Mã này chỉ áp dụng cho đơn có cước từ "
                    + formatMoney(promoCode.getMinOrderAmount()) + "đ trở lên.");
        }
        return promoCode;
    }

    private void requireAvailable(PromoCode promoCode) {
        if (!promoCode.isActive()) {
            throw new IllegalStateException("Mã này đã ngừng áp dụng.");
        }
        if (!promoCode.isWithinPeriod(LocalDateTime.now())) {
            throw new IllegalStateException("Mã này đã hết hạn hoặc chưa tới ngày áp dụng.");
        }
        if (!promoCode.hasRemainingUsage()) {
            throw new IllegalStateException("Mã này đã hết lượt sử dụng.");
        }
        if (promoCode.getDiscountType() == null || promoCode.getDiscountValue() == null
                || promoCode.getDiscountValue().signum() < 0
                || (promoCode.getDiscountType() == PromoCode.DiscountType.PERCENT
                    && promoCode.getDiscountValue().compareTo(ONE_HUNDRED) > 0)
                || (promoCode.getMaxDiscountAmount() != null && promoCode.getMaxDiscountAmount().signum() < 0)) {
            throw new IllegalStateException("Mã giảm giá chưa được cấu hình hợp lệ.");
        }
    }

    /** Hiện số tiền kiểu Việt Nam trong thông báo lỗi: 30000 -> "30.000". */
    private String formatMoney(BigDecimal amount) {
        return String.format("%,d", amount.longValue()).replace(',', '.');
    }

    @Override
    public BigDecimal calculateDiscount(PromoCode promoCode, BigDecimal orderFee) {
        BigDecimal fee = orderFee != null ? orderFee : BigDecimal.ZERO;
        if (promoCode == null || fee.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount;
        if (promoCode.getDiscountType() == PromoCode.DiscountType.PERCENT) {
            discount = fee.multiply(promoCode.getDiscountValue())
                    .divide(ONE_HUNDRED, 0, RoundingMode.DOWN);
            // Lam tron XUONG de he thong khong bao gio giam nhieu hon con so da hua voi khach.
            if (promoCode.getMaxDiscountAmount() != null
                    && discount.compareTo(promoCode.getMaxDiscountAmount()) > 0) {
                discount = promoCode.getMaxDiscountAmount();
            }
        } else {
            discount = promoCode.getDiscountValue();
        }

        // Giam toi da bang dung so cuoc - khong bao gio de khach phai tra so am.
        return discount.min(fee).max(BigDecimal.ZERO).setScale(0, RoundingMode.DOWN);
    }

    @Override
    public void markUsed(PromoCode promoCode) {
        if (promoCode == null) {
            return;
        }
        // Re-read under lock even for callers outside createOrder; never increment a stale detached value.
        PromoCode locked = promoCodeRepository.findByCodeIgnoreCaseForUpdate(promoCode.getCode())
                .orElseThrow(() -> new IllegalStateException("Mã giảm giá không còn tồn tại."));
        requireAvailable(locked);
        if (locked.getUsedCount() == Integer.MAX_VALUE) {
            throw new IllegalStateException("Mã này đã hết lượt sử dụng.");
        }
        locked.setUsedCount(locked.getUsedCount() + 1);
        locked.setUpdatedAt(LocalDateTime.now());
        promoCodeRepository.save(locked);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PromoCode> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return promoCodeRepository.findByCodeIgnoreCase(code.trim());
    }
}
