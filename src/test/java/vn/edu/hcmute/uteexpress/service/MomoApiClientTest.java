package vn.edu.hcmute.uteexpress.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.edu.hcmute.uteexpress.config.MomoProperties;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentAttempt;

import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MomoApiClientTest {
    private static final String REF = "UTE-12-Q-aabbccdd";
    ObjectMapper mapper = new ObjectMapper();
    MomoProperties properties;
    MomoApiClient client;
    MockRestServiceServer server;
    MomoPaymentAttempt attempt = new MomoPaymentAttempt(12L, REF, 30000, "Cuoc van chuyen UTEExpress UTE0012", false);

    @BeforeEach void setup() {
        properties = new MomoProperties(true, MomoProperties.TEST_ENDPOINT, "TEST_PARTNER", "access", "test_secret", "http://localhost:8080");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MomoApiClient(properties, builder);
    }

    private Map<String, Object> response(String requestId) {
        Map<String, Object> result = new HashMap<>();
        result.put("partnerCode", "TEST_PARTNER"); result.put("orderId", REF); result.put("requestId", requestId);
        result.put("amount", 30000); result.put("resultCode", 0); result.put("message", "Successful.");
        result.put("payUrl", "https://test-payment.momo.vn/v2/gateway/pay?t=test");
        result.put("qrCodeUrl", "momo://app?test=1"); result.put("responseTime", 1791768000000L);
        result.put("transId", 1234567890L);
        return result;
    }

    @Test void qrCreateUsesServerAmountIdAndCanonicalSignature() throws Exception {
        server.expect(requestTo(MomoProperties.TEST_ENDPOINT + "/v2/gateway/api/create")).andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    var body = mapper.readValue(((MockClientHttpRequest) request).getBodyAsString(), new TypeReference<Map<String, Object>>() { });
                    assertThat(body).containsEntry("requestType", "captureWallet").containsEntry("amount", 30000)
                            .containsEntry("orderId", REF).containsEntry("requestId", REF).containsEntry("autoCapture", true);
                    String raw = "accessKey=access&amount=30000&extraData=&ipnUrl=http://localhost:8080/thanh-toan/momo/ipn"
                            + "&orderId=" + REF + "&orderInfo=Cuoc van chuyen UTEExpress UTE0012&partnerCode=TEST_PARTNER"
                            + "&redirectUrl=http://localhost:8080/thanh-toan/momo/ket-qua&requestId=" + REF + "&requestType=captureWallet";
                    assertThat(body.get("signature")).isEqualTo(properties.sign(raw));
                }).andRespond(withSuccess(mapper.writeValueAsString(response(REF)), MediaType.APPLICATION_JSON));
        var checkout = client.create(attempt, true);
        assertThat(checkout.qrContent()).isEqualTo("momo://app?test=1");
        assertThat(checkout.reference()).isEqualTo(REF);
        server.verify();
    }

    @Test void hostedCreateSelectsMoMoHostedMethods() throws Exception {
        server.expect(requestTo(MomoProperties.TEST_ENDPOINT + "/v2/gateway/api/create"))
                .andExpect(jsonPath("$.requestType").value("payWithMethod"))
                .andRespond(withSuccess(mapper.writeValueAsString(response(REF)), MediaType.APPLICATION_JSON));
        assertThat(client.create(attempt, false).qrContent()).isEmpty(); server.verify();
    }

    @Test void queryUsesUniqueSignedRequestAndChecksEchoedIdentityAndAmount() {
        server.expect(requestTo(MomoProperties.TEST_ENDPOINT + "/v2/gateway/api/query")).andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    var body = mapper.readValue(((MockClientHttpRequest) request).getBodyAsString(), new TypeReference<Map<String, Object>>() { });
                    String requestId = (String) body.get("requestId");
                    assertThat(requestId).startsWith("QUERY-").isNotEqualTo(REF);
                    assertThat(body.get("signature")).isEqualTo(properties.sign("accessKey=access&orderId=" + REF
                            + "&partnerCode=TEST_PARTNER&requestId=" + requestId));
                    return withSuccess(mapper.writeValueAsString(response(requestId)), MediaType.APPLICATION_JSON).createResponse(request);
                });
        assertThat(client.query(attempt)).containsEntry("transId", "1234567890"); server.verify();
    }

    @Test void wrongAmountCreateDoesNotAcceptPaymentPage() throws Exception {
        var wrong = response(REF); wrong.put("amount", 1);
        server.expect(anything()).andRespond(withSuccess(mapper.writeValueAsString(wrong), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.create(attempt, true)).hasMessageContaining("không khớp"); server.verify();
    }

    @Test void externalPayUrlCannotRedirectUserToAnotherHost() throws Exception {
        var wrong = response(REF); wrong.put("payUrl", "https://evil.example/pay");
        server.expect(anything()).andRespond(withSuccess(mapper.writeValueAsString(wrong), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.create(attempt, false)).hasMessageContaining("không hợp lệ"); server.verify();
    }

    @Test void forgedCreateSignatureIsRejected() throws Exception {
        var wrong = response(REF); wrong.put("signature", "0".repeat(64));
        server.expect(anything()).andRespond(withSuccess(mapper.writeValueAsString(wrong), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.create(attempt, true)).hasMessageContaining("chữ ký"); server.verify();
    }

    @Test void wrongQueryPartnerRequestCurrencyAndAmountAreRejected() {
        for (Map.Entry<String, Object> wrong : Map.<String, Object>of("partnerCode", "OTHER", "requestId", "wrong",
                "currency", "USD", "amount", 1).entrySet()) {
            server.reset();
            server.expect(anything()).andRespond(request -> {
                var body = mapper.readValue(((MockClientHttpRequest) request).getBodyAsString(), new TypeReference<Map<String, Object>>() { });
                var result = response((String) body.get("requestId")); result.put(wrong.getKey(), wrong.getValue());
                return withSuccess(mapper.writeValueAsString(result), MediaType.APPLICATION_JSON).createResponse(request);
            });
            assertThatThrownBy(() -> client.query(attempt)).isInstanceOf(IllegalStateException.class); server.verify();
        }
    }

    @Test void unavailableGatewayReportsFailureWithoutInventingSuccess() {
        server.expect(anything()).andRespond(withServerError());
        assertThatThrownBy(() -> client.create(attempt, true)).hasMessageContaining("Chưa xác nhận"); server.verify();
    }
}
