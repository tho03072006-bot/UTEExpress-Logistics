package vn.edu.hcmute.uteexpress.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.edu.hcmute.uteexpress.config.MomoProperties;
import vn.edu.hcmute.uteexpress.dto.MomoCheckout;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentAttempt;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Gateway HTTP boundary, adapted from the cinema project. No payment outcome is fabricated. */
@Component
public class MomoApiClient {
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() { };
    private final MomoProperties properties;
    private final RestClient restClient;

    @Autowired
    public MomoApiClient(MomoProperties properties) {
        this(properties, defaultBuilder());
    }

    /** Constructor for offline HTTP contract tests. */
    public MomoApiClient(MomoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public MomoCheckout create(MomoPaymentAttempt attempt, boolean qr) {
        properties.requireEnabled();
        String requestType = qr ? "captureWallet" : "payWithMethod";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", properties.getPartnerCode());
        body.put("requestId", attempt.reference());
        body.put("orderId", attempt.reference());
        body.put("amount", attempt.amount());
        body.put("orderInfo", attempt.orderInfo());
        body.put("redirectUrl", properties.getRedirectUrl());
        body.put("ipnUrl", properties.getIpnUrl());
        body.put("requestType", requestType);
        body.put("extraData", "");
        body.put("autoCapture", true);
        body.put("lang", "vi");
        body.put("signature", properties.sign("accessKey=" + properties.getAccessKey()
                + "&amount=" + attempt.amount() + "&extraData=&ipnUrl=" + properties.getIpnUrl()
                + "&orderId=" + attempt.reference() + "&orderInfo=" + attempt.orderInfo()
                + "&partnerCode=" + properties.getPartnerCode() + "&redirectUrl=" + properties.getRedirectUrl()
                + "&requestId=" + attempt.reference() + "&requestType=" + requestType));
        Map<String, String> response = post("/v2/gateway/api/create", body);
        verifyIdentity(response, attempt.reference(), attempt.reference());
        verifyAmount(response, attempt.amount());
        if (!"0".equals(response.get("resultCode"))) {
            // Retrying after an ambiguous timeout never creates another requestId.
            throw new IllegalStateException("MoMo chưa tạo được trang thanh toán. Hãy kiểm tra trạng thái giao dịch trước khi thử lại.");
        }
        String payUrl = safePayUrl(value(response, "payUrl"));
        if (!value(response, "signature").isBlank()) {
            String raw = "accessKey=" + properties.getAccessKey() + "&amount=" + value(response, "amount")
                    + "&message=" + value(response, "message") + "&orderId=" + value(response, "orderId")
                    + "&partnerCode=" + value(response, "partnerCode") + "&payUrl=" + payUrl
                    + "&requestId=" + value(response, "requestId") + "&responseTime=" + value(response, "responseTime")
                    + "&resultCode=" + value(response, "resultCode");
            if (!properties.matchesSignature(raw, response.get("signature"))) {
                throw new IllegalStateException("Phản hồi tạo giao dịch MoMo có chữ ký không hợp lệ.");
            }
        }
        String qrContent = qr ? value(response, "qrCodeUrl") : "";
        if (qr && (qrContent.isBlank() || qrContent.length() > 4096)) {
            throw new IllegalStateException("MoMo chưa trả về mã QR. Vui lòng tiếp tục tại trang thanh toán MoMo hoặc kiểm tra trạng thái.");
        }
        return new MomoCheckout(attempt.orderId(), attempt.reference(), attempt.amount(), payUrl, qrContent);
    }

    /** The documented query response is authenticated by server HTTPS, not browser parameters. */
    public Map<String, String> query(MomoPaymentAttempt attempt) {
        properties.requireEnabled();
        String requestId = "QUERY-" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", properties.getPartnerCode());
        body.put("orderId", attempt.reference());
        body.put("requestId", requestId);
        body.put("lang", "vi");
        body.put("signature", properties.sign("accessKey=" + properties.getAccessKey()
                + "&orderId=" + attempt.reference() + "&partnerCode=" + properties.getPartnerCode()
                + "&requestId=" + requestId));
        Map<String, String> response = post("/v2/gateway/api/query", body);
        verifyIdentity(response, attempt.reference(), requestId);
        verifyAmount(response, attempt.amount());
        return response;
    }

    public void verifyIdentity(Map<String, String> response, String orderId, String requestId) {
        if (!properties.getPartnerCode().equals(value(response, "partnerCode"))
                || !orderId.equals(value(response, "orderId")) || !requestId.equals(value(response, "requestId"))) {
            throw new IllegalStateException("Phản hồi MoMo không khớp đối tác hoặc mã giao dịch.");
        }
        if (response.containsKey("currency") && !"VND".equals(response.get("currency"))) {
            throw new IllegalStateException("Giao dịch MoMo phải dùng VND.");
        }
    }

    public void verifyAmount(Map<String, String> response, long expected) {
        if (positiveLong(value(response, "amount"), "Số tiền MoMo không hợp lệ.") != expected) {
            throw new IllegalStateException("Số tiền MoMo không khớp cước vận chuyển.");
        }
    }

    public static long positiveLong(String value, String message) {
        try {
            if (value != null && value.matches("[1-9][0-9]{0,18}")) {
                long parsed = Long.parseLong(value);
                if (parsed > 0) return parsed;
            }
        } catch (NumberFormatException ignored) { }
        throw new IllegalStateException(message);
    }

    private String safePayUrl(String url) {
        try {
            URI uri = URI.create(url);
            if ("https".equals(uri.getScheme()) && "test-payment.momo.vn".equals(uri.getHost())
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)) return url;
        } catch (IllegalArgumentException ignored) { }
        throw new IllegalStateException("Địa chỉ thanh toán MoMo TEST không hợp lệ.");
    }

    private Map<String, String> post(String path, Map<String, Object> body) {
        try {
            Map<String, Object> response = restClient.post().uri(properties.getEndpoint() + path)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JSON_MAP);
            if (response == null) throw new IllegalStateException("MoMo trả về phản hồi trống. Vui lòng kiểm tra lại trạng thái.");
            Map<String, String> result = new LinkedHashMap<>();
            response.forEach((key, value) -> result.put(key, value == null ? "" : String.valueOf(value)));
            return result;
        } catch (RestClientException ex) {
            // Never include request body / credentials / signed payUrl in log or exception output.
            throw new IllegalStateException("Không kết nối được MoMo. Chưa xác nhận thanh toán; hãy kiểm tra trạng thái sau ít phút.");
        }
    }

    private static RestClient.Builder defaultBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return RestClient.builder().requestFactory(factory);
    }

    public static String value(Map<String, String> values, String key) { return values.getOrDefault(key, ""); }
}
