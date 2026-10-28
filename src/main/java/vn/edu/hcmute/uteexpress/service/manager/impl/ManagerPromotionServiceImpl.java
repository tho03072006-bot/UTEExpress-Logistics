package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.PromoCodeRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.service.manager.*;

@Service @Transactional
public class ManagerPromotionServiceImpl implements ManagerPromotionService {
    private final PromoCodeRepository promotions;
    private final StaffAccessPolicy access;
    private final AuditService audit;
    private final Clock clock;
    public ManagerPromotionServiceImpl(PromoCodeRepository promotions, StaffAccessPolicy access, AuditService audit, Clock clock) {
        this.promotions = promotions; this.access = access; this.audit = audit; this.clock = clock;
    }
    @Override @Transactional(readOnly = true)
    public Page<PromotionResponse> findPromotions(String actor, int page) {
        access.requireOperator(actor); access.validatePage(page);
        return promotions.findAll(PageRequest.of(page, 20, Sort.by("id").descending())).map(PromotionResponse::from);
    }
    @Override @Transactional(readOnly = true)
    public PromotionRequest preparePromotion(String actor, Long id) {
        access.requireOperator(actor); access.validateId(id);
        var promo = promotions.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khuyến mãi."));
        var form = new PromotionRequest();
        form.setRevision(promo.getRevision()); form.setCode(promo.getCode()); form.setDescription(promo.getDescription());
        form.setDiscountType(promo.getDiscountType()); form.setDiscountValue(promo.getDiscountValue());
        form.setMaxDiscountAmount(promo.getMaxDiscountAmount()); form.setMinOrderAmount(promo.getMinOrderAmount());
        form.setStartAt(promo.getStartAt()); form.setEndAt(promo.getEndAt());
        form.setUsageLimit(promo.getUsageLimit()); form.setActive(promo.isActive());
        return form;
    }
    @Override
    public void savePromotion(String actor, Long id, PromotionRequest form) {
        access.requireOperator(actor); access.validate(form);
        if (form.getStartAt().getYear() < 2000 || form.getEndAt().getYear() > 2100) {
            throw new IllegalArgumentException("Năm khuyến mãi phải trong khoảng 2000–2100.");
        }
        PromoCode promo;
        if (id == null) {
            if (!form.getEndAt().isAfter(LocalDateTime.now(clock))) {
                throw new IllegalArgumentException("Khuyến mãi mới phải còn thời hạn sử dụng.");
            }
            if (promotions.findByCodeIgnoreCase(form.getCode()).isPresent()) {
                throw new IllegalArgumentException("Mã khuyến mãi đã tồn tại.");
            }
            promo = new PromoCode();
        } else {
            access.validateId(id);
            promo = promotions.lockById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khuyến mãi."));
            if (promo.getRevision() != form.getRevision()) {
                throw new StaffConflictException("Khuyến mãi vừa thay đổi hoặc có lượt sử dụng mới. Hãy tải lại.");
            }
            if (!promo.getCode().equals(form.getCode())) {
                throw new IllegalArgumentException("Không được đổi mã của khuyến mãi đã tạo.");
            }
            if (form.getUsageLimit() < promo.getUsedCount()) {
                throw new IllegalArgumentException("Giới hạn lượt không được thấp hơn số lượt đã dùng.");
            }
            if (promo.getUsedCount() > 0 && !sameTerms(promo, form)) {
                throw new IllegalArgumentException("Mã đã được dùng: chỉ sửa mô tả, trạng thái và tổng lượt; tạo mã mới nếu cần đổi điều kiện.");
            }
            if (form.getActive() && !form.getEndAt().isAfter(LocalDateTime.now(clock))) {
                throw new IllegalArgumentException("Không thể bật khuyến mãi đã hết hạn.");
            }
            if (sameTerms(promo, form) && Objects.equals(promo.getDescription(), form.getDescription())
                && Objects.equals(promo.getUsageLimit(), form.getUsageLimit()) && promo.isActive() == form.getActive()) {
                throw new IllegalArgumentException("Chưa có thay đổi khuyến mãi.");
            }
        }
        promo.setCode(form.getCode()); promo.setDescription(form.getDescription());
        promo.setDiscountType(form.getDiscountType()); promo.setDiscountValue(form.getDiscountValue());
        promo.setMaxDiscountAmount(form.getMaxDiscountAmount()); promo.setMinOrderAmount(form.getMinOrderAmount());
        promo.setStartAt(form.getStartAt()); promo.setEndAt(form.getEndAt());
        promo.setUsageLimit(form.getUsageLimit()); promo.setActive(form.getActive());
        promo.setUpdatedAt(LocalDateTime.now(clock));
        promotions.saveAndFlush(promo);
        audit.record(actor, id == null ? "PROMOTION_CREATE" : "PROMOTION_UPDATE",
            "promo:" + promo.getId(), "Mã " + promo.getCode() + "; trạng thái=" + promo.isActive()
                + "; tổng lượt=" + promo.getUsageLimit() + "; đã dùng=" + promo.getUsedCount());
    }
    private boolean sameTerms(PromoCode promo, PromotionRequest form) {
        return promo.getDiscountType() == form.getDiscountType()
            && equalMoney(promo.getDiscountValue(), form.getDiscountValue())
            && equalMoney(promo.getMaxDiscountAmount(), form.getMaxDiscountAmount())
            && equalMoney(promo.getMinOrderAmount(), form.getMinOrderAmount())
            && Objects.equals(promo.getStartAt(), form.getStartAt()) && Objects.equals(promo.getEndAt(), form.getEndAt());
    }

    @Override
    public void changePromotionStatus(String actor, Long id, PromotionStatusRequest form) {
        access.requireOperator(actor); access.validateId(id); access.validate(form);
        var promo = promotions.lockById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khuyến mãi."));
        if (promo.getRevision() != form.revision()) { throw new StaffConflictException("Khuyến mãi vừa thay đổi. Hãy tải lại."); }
        if (promo.isActive() == form.active()) { throw new IllegalArgumentException("Trạng thái không thay đổi."); }
        if (form.active() && ((promo.getEndAt() != null && !promo.getEndAt().isAfter(LocalDateTime.now(clock)))
            || !promo.hasRemainingUsage())) {
            throw new IllegalArgumentException("Không thể bật mã đã hết hạn hoặc hết lượt.");
        }
        promo.setActive(form.active()); promo.setUpdatedAt(LocalDateTime.now(clock));
        promotions.saveAndFlush(promo);
        audit.record(actor, "PROMOTION_STATUS", "promo:" + id, "Mã " + promo.getCode() + "; active=" + promo.isActive());
    }
    private boolean equalMoney(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }
}

