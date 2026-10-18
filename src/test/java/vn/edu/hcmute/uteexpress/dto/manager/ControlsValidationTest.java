package vn.edu.hcmute.uteexpress.dto.manager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
import static org.junit.jupiter.api.Assertions.*;

class ControlsValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void open() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }
    static Stream<Arguments> invalidPolicies() {
        return Stream.of(
            new OperationsPolicyRequest(null,24,120,48,4,3,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(-1L,24,120,48,4,3,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,0,120,48,4,3,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,169,120,48,4,3,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,null,48,4,3,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,721,48,4,3,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,120,0,4,3,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,120,48,721,3,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,120,48,4,0,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,120,48,4,11,24,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,120,48,4,3,169,168,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,120,48,4,3,24,2161,"Lý do hợp lệ"),
            new OperationsPolicyRequest(0L,24,120,48,4,3,24,168,"no"),
            new OperationsPolicyRequest(0L,24,120,48,4,3,24,168," ".repeat(8)),
            new OperationsPolicyRequest(0L,24,120,48,4,3,24,168,"a".repeat(501))
        ).map(Arguments::of);
    }
    @ParameterizedTest @MethodSource("invalidPolicies") void invalidPolicyFieldsReject(OperationsPolicyRequest form) {
        assertFalse(validator.validate(form).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "1000000000000"})
    void moneyOutsideWholeVndRangeRejects(String amount) {
        var form = new OperationsCaseRequest(OperationsCase.Kind.CASH_SUPPLEMENT,1L,0L,new BigDecimal(amount),null,
                "Lý do hợp lệ","RECEIPT-01",UUID.randomUUID().toString());
        assertFalse(validator.validate(form).isEmpty());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"bad-token", "../secret", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void requestTokenMustBeUuid(String token) {
        var form = new OperationsCaseRequest(OperationsCase.Kind.RETURN,1L,0L,null,null,"Lý do hợp lệ",null,token);
        assertFalse(validator.validate(form).isEmpty());
    }
    @Test void missingIdsRevisionAndKindReject() {
        var form = new OperationsCaseRequest(null,-1L,null,null,null," ",null,UUID.randomUUID().toString());
        assertTrue(validator.validate(form).size() >= 4);
        assertFalse(validator.validate(new OperationsReviewRequest(null,null,"no")).isEmpty());
    }
    @Test void validMoneyAndTariffBoundariesPassBeanValidation() {
        assertTrue(validator.validate(new OperationsCaseRequest(OperationsCase.Kind.CASH_REFUND,1L,0L,new BigDecimal("999999999999"),null,
                "Lý do hợp lệ","RECEIPT-01",UUID.randomUUID().toString())).isEmpty());
        assertTrue(validator.validate(new TariffScheduleRequest(Order.ServiceType.STANDARD,new BigDecimal("100000000"),new BigDecimal("5000000"),
                LocalDateTime.now().plusDays(1),LocalDateTime.now().plusDays(2),"Lý do hợp lệ")).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "1.5", "100000001", "999999999"})
    void scheduledBaseFeeUsesSameLimitsAsImmediateTariff(String value) {
        assertFalse(validator.validate(new TariffScheduleRequest(Order.ServiceType.STANDARD, new BigDecimal(value), BigDecimal.ZERO,
            LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), "Lý do hợp lệ")).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "1.5", "5000001", "999999999"})
    void scheduledPerKgFeeUsesSameLimitsAsImmediateTariff(String value) {
        assertFalse(validator.validate(new TariffScheduleRequest(Order.ServiceType.STANDARD, BigDecimal.ZERO, new BigDecimal(value),
            LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), "Lý do hợp lệ")).isEmpty());
    }
}
