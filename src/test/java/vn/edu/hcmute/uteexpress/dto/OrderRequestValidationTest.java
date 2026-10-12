package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.validation.OrderRequestRules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRequestValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeValidator() { FACTORY.close(); }

    @Test void normalizesNamesAddressesPhoneAndCodeBeforeValidation() {
        OrderCreateRequest request = validRequest();
        request.setSenderName("  Nguyễn Thọ  ");
        request.setReceiverPhone(" 0901234567 ");
        request.setPromoCode("  free30 ");
        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.getSenderName()).isEqualTo("Nguyễn Thọ");
        assertThat(request.getReceiverPhone()).isEqualTo("0901234567");
        assertThat(request.getPromoCode()).isEqualTo("FREE30");
        OrderRequestRules.validate(request);
    }

    @Test void rejectsNamesAndAddressesBeyondColumnSizeAndMalformedPhone() {
        OrderCreateRequest request = validRequest();
        request.setSenderName("a".repeat(101));
        request.setReceiverAddress("b".repeat(256));
        request.setReceiverPhone("abc");
        request.setPromoCode("c".repeat(31));
        assertThat(VALIDATOR.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("senderName", "receiverAddress", "receiverPhone", "promoCode");
        assertThatThrownBy(() -> OrderRequestRules.validate(request)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, 0, 0.099, 100.001, 1000, 1.0001,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void createAndQuoteUseTheSameFiniteWeightPolicy(double weight) {
        OrderCreateRequest order = validRequest();
        order.setWeightKg(weight);
        FeeEstimateRequest quote = new FeeEstimateRequest();
        quote.setWeightKg(weight);
        assertThat(VALIDATOR.validate(order)).extracting(v -> v.getPropertyPath().toString()).contains("weightKg");
        assertThat(VALIDATOR.validate(quote)).isNotEmpty();
        assertThatThrownBy(() -> OrderRequestRules.validate(order)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest @ValueSource(doubles = {0.1, 0.333, 99.999, 100})
    void acceptsSupportedWeightBoundaries(double weight) {
        OrderCreateRequest request = validRequest();
        request.setWeightKg(weight);
        assertThat(VALIDATOR.validate(request)).isEmpty();
        OrderRequestRules.validate(request);
    }

    @Test void quoteMayStartEmptyButSubmittedOrderRequiresWeight() {
        assertThat(VALIDATOR.validate(new FeeEstimateRequest())).isEmpty();
        OrderCreateRequest request = validRequest();
        request.setWeightKg(null);
        assertThat(VALIDATOR.validate(request)).extracting(v -> v.getPropertyPath().toString()).contains("weightKg");
    }

    private static OrderCreateRequest validRequest() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setSenderName("Người gửi");
        request.setSenderAddress("1 Võ Văn Ngân, TP.HCM");
        request.setReceiverName("Người nhận");
        request.setReceiverAddress("2 Nguyễn Trãi, Hà Nội");
        request.setReceiverPhone("0901234567");
        request.setWeightKg(1.0);
        request.setServiceType(Order.ServiceType.STANDARD);
        return request;
    }
}
