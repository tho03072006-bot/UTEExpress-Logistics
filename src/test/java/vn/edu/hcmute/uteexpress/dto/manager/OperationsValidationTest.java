package vn.edu.hcmute.uteexpress.dto.manager;

import jakarta.validation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import static org.junit.jupiter.api.Assertions.*;

class OperationsValidationTest {
    private ValidatorFactory factory;
    private Validator validator;
    @BeforeEach void setup() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterEach void close() { factory.close(); }
    private OrderCreateRequest order() {
        var form = new OrderCreateRequest(); form.setSenderName("Nguyễn Thắng"); form.setSenderAddress("Số 1 Võ Văn Ngân");
        form.setReceiverName("Trần Minh Thọ"); form.setReceiverPhone("+84 901 234 567");
        form.setReceiverAddress("Số 2 Võ Văn Ngân"); form.setWeightKg(1.0); form.setServiceType(Order.ServiceType.STANDARD);
        return form;
    }
    @Test void vietnameseNamesAndInternationalPhoneNormalizeCorrectly() {
        var form = order(); assertTrue(validator.validate(form).isEmpty());
        assertEquals("0901234567", form.getReceiverPhone());
    }
    @ParameterizedTest @ValueSource(strings = {"123", "0123456789", "09012345678", "abc", "<script>", ""})
    void invalidPhoneRejects(String phone) {
        var form = order(); form.setReceiverPhone(phone); assertFalse(validator.validate(form).isEmpty());
    }
    @ParameterizedTest @ValueSource(doubles = {0, -1, 101, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void badWeightRejectsBeforeCreatingOrder(double weight) {
        var form = order(); form.setWeightKg(weight); assertFalse(validator.validate(form).isEmpty());
    }
    @Test void oversizedAndControlCharacterInputCannotReachDatabase() {
        var form = order(); form.setSenderName("N".repeat(101)); assertFalse(validator.validate(form).isEmpty());
        form = order(); form.setReceiverAddress("A".repeat(256)); assertFalse(validator.validate(form).isEmpty());
        form = order(); form.setSenderAddress("Địa chỉ\u0000ẩn"); assertFalse(validator.validate(form).isEmpty());
        form = order(); form.setPromoCode("A".repeat(31)); assertFalse(validator.validate(form).isEmpty());
    }
    private PromotionRequest promotion() {
        var form = new PromotionRequest(); form.setCode("SALE10"); form.setDescription("Giảm cước");
        form.setDiscountType(PromoCode.DiscountType.PERCENT); form.setDiscountValue(BigDecimal.TEN);
        form.setMaxDiscountAmount(BigDecimal.valueOf(30000)); form.setMinOrderAmount(BigDecimal.ZERO);
        form.setUsageLimit(10); form.setStartAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        form.setEndAt(form.getStartAt().plusDays(30)); return form;
    }
    @Test void promotionCrossFieldValidationHandlesEveryDiscountType() {
        var form = promotion(); assertTrue(validator.validate(form).isEmpty());
        form.setDiscountValue(BigDecimal.valueOf(101)); assertFalse(validator.validate(form).isEmpty());
        form = promotion(); form.setDiscountType(PromoCode.DiscountType.FIXED_AMOUNT);
        assertFalse(validator.validate(form).isEmpty()); form.setMaxDiscountAmount(null);
        assertTrue(validator.validate(form).isEmpty());
    }
    @Test void invalidMoneyUsagePeriodAndControlTextReject() {
        var form = promotion(); form.setDiscountValue(new BigDecimal("1.5")); assertFalse(validator.validate(form).isEmpty());
        form = promotion(); form.setUsageLimit(0); assertFalse(validator.validate(form).isEmpty());
        form = promotion(); form.setEndAt(form.getStartAt()); assertFalse(validator.validate(form).isEmpty());
        form = promotion(); form.setEndAt(form.getStartAt().plusDays(367)); assertFalse(validator.validate(form).isEmpty());
        form = promotion(); form.setDescription("Lý do\nẩn"); assertFalse(validator.validate(form).isEmpty());
        form = promotion(); form.setCode("INVALID CODE"); assertFalse(validator.validate(form).isEmpty());
    }
}

