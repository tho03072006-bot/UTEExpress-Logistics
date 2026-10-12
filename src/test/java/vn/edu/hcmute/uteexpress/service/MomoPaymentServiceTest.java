package vn.edu.hcmute.uteexpress.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import vn.edu.hcmute.uteexpress.config.MomoProperties;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentAttempt;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class MomoPaymentServiceTest {
    private static final String REF = "UTE-12-Q-aabbccdd";
    @Mock OrderPaymentService payments;
    MomoProperties properties;
    MomoApiClient api;
    MomoPaymentService service;

    @BeforeEach void setup() {
        properties = new MomoProperties(true, MomoProperties.TEST_ENDPOINT, "TEST_PARTNER", "access", "test_secret", "http://localhost:8080");
        api = new MomoApiClient(properties, RestClient.builder());
        service = new MomoPaymentService(properties, api, payments);
    }

    Map<String, String> callback(String code) {
        Map<String, String> result = new HashMap<>();
        result.put("partnerCode", "TEST_PARTNER"); result.put("orderId", REF); result.put("requestId", REF);
        result.put("amount", "30000"); result.put("extraData", ""); result.put("message", "Successful.");
        result.put("orderInfo", "Cuoc van chuyen UTEExpress UTE0012"); result.put("orderType", "momo_wallet");
        result.put("payType", "qr"); result.put("responseTime", "1791768000000"); result.put("resultCode", code);
        result.put("transId", "1234567890");
        return sign(result);
    }

    Map<String, String> sign(Map<String, String> params) {
        params.put("signature", properties.sign(MomoPaymentService.callbackRawSignature("access", params)));
        return params;
    }

    @Test void validSignedIpnSettlesExpectedShippingAmount() {
        var result = service.handleIpn(callback("0"));
        assertThat(result.state()).isEqualTo(MomoPaymentResult.State.PAID);
        verify(payments).settleVerifiedMomo(12L, REF, 30000, "1234567890");
    }

    @Test void tamperedAmountAndForgedSignaturesNeverTouchLedger() {
        var changed = callback("0"); changed.put("amount", "1");
        assertThatThrownBy(() -> service.handleIpn(changed)).hasMessageContaining("chữ ký");
        var forged = callback("0"); forged.put("signature", "0".repeat(64));
        assertThatThrownBy(() -> service.handleIpn(forged)).hasMessageContaining("chữ ký");
        var missing = callback("0"); missing.remove("signature");
        assertThatThrownBy(() -> service.handleIpn(missing)).hasMessageContaining("chữ ký");
        verifyNoInteractions(payments);
    }

    @Test void signedWrongAmountStillRequiresDatabaseAmountMatch() {
        var changed = callback("0"); changed.put("amount", "30001"); sign(changed);
        when(payments.settleVerifiedMomo(12L, REF, 30001, "1234567890"))
                .thenThrow(new IllegalStateException("Số tiền không khớp"));
        assertThatThrownBy(() -> service.handleIpn(changed)).hasMessageContaining("không khớp");
    }

    @Test void wrongPartnerRequestIdCurrencyAndForeignReferenceAreRejectedEvenIfSigned() {
        for (Map.Entry<String, String> wrong : Map.of("partnerCode", "OTHER", "requestId", "OTHER_REQ",
                "currency", "USD", "orderId", "CINEMA-12").entrySet()) {
            var params = callback("0"); params.put(wrong.getKey(), wrong.getValue()); sign(params);
            assertThatThrownBy(() -> service.handleIpn(params)).isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(payments);
    }

    @Test void malformedSuccessTransactionStatusAndAmountAreRejected() {
        for (Map.Entry<String, String> wrong : Map.of("transId", "9223372036854775808", "amount", "-30000",
                "resultCode", "NaN", "responseTime", "0").entrySet()) {
            var params = callback("0"); params.put(wrong.getKey(), wrong.getValue()); sign(params);
            assertThatThrownBy(() -> service.handleIpn(params)).isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(payments);
    }

    @Test void pendingAndFailureNeverDowngradePaidState() {
        assertThat(service.handleIpn(callback("1000")).state()).isEqualTo(MomoPaymentResult.State.PENDING);
        assertThat(service.handleIpn(callback("1006")).state()).isEqualTo(MomoPaymentResult.State.FAILED);
        verifyNoInteractions(payments);
    }

    @Test void alreadyPaidQueryStillChecksOwnerButDoesNotCallGateway() {
        when(payments.findOwnedMomoAttempt(12L, "owner")).thenReturn(new MomoPaymentAttempt(12L, REF, 30000, "fee", true));
        assertThat(service.check(12L, "owner").state()).isEqualTo(MomoPaymentResult.State.PAID);
        verify(payments).findOwnedMomoAttempt(12L, "owner");
        verify(payments, never()).settleVerifiedMomo(any(), any(), anyLong(), any());
    }

    @Test void noKeysOrProductionEndpointDisableCheckout() {
        assertThat(new MomoProperties(true, MomoProperties.TEST_ENDPOINT, "p", "a", "", "http://localhost:8080").isEnabled()).isFalse();
        assertThat(new MomoProperties(true, "https://payment.momo.vn", "p", "a", "s", "http://localhost:8080").isEnabled()).isFalse();
        assertThat(new MomoProperties(false, MomoProperties.TEST_ENDPOINT, "p", "a", "s", "http://localhost:8080").isEnabled()).isFalse();
        var disabled = new MomoPaymentService(new MomoProperties(false, MomoProperties.TEST_ENDPOINT, "p", "a", "s", "http://localhost:8080"), api, payments);
        assertThatThrownBy(() -> disabled.start(12L, "owner", true)).hasMessageContaining("chưa sẵn sàng");
        verifyNoInteractions(payments);
    }

    @Test void verifiedServerQueryCanSettleFeeWithoutPublicCallback() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var queryService = new MomoPaymentService(properties, new MomoApiClient(properties, builder), payments);
        when(payments.findOwnedMomoAttempt(12L, "owner")).thenReturn(new MomoPaymentAttempt(12L, REF, 30000, "fee", false));
        var mapper = new ObjectMapper();
        server.expect(requestTo(MomoProperties.TEST_ENDPOINT + "/v2/gateway/api/query")).andRespond(request -> {
            var body = mapper.readValue(((MockClientHttpRequest) request).getBodyAsString(), new TypeReference<Map<String, Object>>() { });
            var response = Map.of("partnerCode", "TEST_PARTNER", "orderId", REF, "requestId", body.get("requestId"),
                    "amount", 30000, "resultCode", 0, "transId", "1234567890", "currency", "VND");
            return withSuccess(mapper.writeValueAsString(response), MediaType.APPLICATION_JSON).createResponse(request);
        });
        assertThat(queryService.check(12L, "owner").state()).isEqualTo(MomoPaymentResult.State.PAID);
        verify(payments).settleVerifiedMomo(12L, REF, 30000, "1234567890"); server.verify();
    }
}
