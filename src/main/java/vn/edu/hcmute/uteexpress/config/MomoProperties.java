package vn.edu.hcmute.uteexpress.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** MoMo TEST only. Credentials belong in ignored local configuration or environment variables. */
@Component
public class MomoProperties {
    public static final String TEST_ENDPOINT = "https://test-payment.momo.vn";
    private final boolean enabled;
    private final String endpoint;
    private final String partnerCode;
    private final String accessKey;
    private final String secretKey;
    private final String baseUrl;

    public MomoProperties(@Value("${momo.enabled:false}") boolean enabled,
                          @Value("${momo.endpoint:https://test-payment.momo.vn}") String endpoint,
                          @Value("${momo.partner-code:}") String partnerCode,
                          @Value("${momo.access-key:}") String accessKey,
                          @Value("${momo.secret-key:}") String secretKey,
                          @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.enabled = enabled;
        this.endpoint = trimSlash(endpoint);
        this.partnerCode = clean(partnerCode);
        this.accessKey = clean(accessKey);
        this.secretKey = clean(secretKey);
        this.baseUrl = trimSlash(baseUrl);
    }

    public boolean isEnabled() {
        return enabled && TEST_ENDPOINT.equals(endpoint) && configured(partnerCode)
                && configured(accessKey) && configured(secretKey) && validBaseUrl();
    }

    public void requireEnabled() {
        if (!isEnabled()) throw new IllegalStateException("Thanh toán MoMo TEST chưa sẵn sàng. Vui lòng thử lại sau.");
    }

    public String sign(String rawData) {
        requireEnabled();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawData.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không tạo được chữ ký MoMo.", ex);
        }
    }

    public boolean matchesSignature(String rawData, String signature) {
        if (signature == null || !signature.matches("[0-9a-fA-F]{64}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(sign(rawData)), HexFormat.of().parseHex(signature));
    }

    public String getEndpoint() { return endpoint; }
    public String getPartnerCode() { return partnerCode; }
    public String getAccessKey() { return accessKey; }
    public String getRedirectUrl() { return baseUrl + "/thanh-toan/momo/ket-qua"; }
    public String getIpnUrl() { return baseUrl + "/thanh-toan/momo/ipn"; }

    private boolean validBaseUrl() {
        try {
            URI uri = URI.create(baseUrl);
            boolean scheme = "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())));
            return scheme && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getRawQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException ex) { return false; }
    }

    private static boolean configured(String value) {
        return !value.isBlank() && !value.startsWith("CHANGE_ME") && !value.startsWith("YOUR_");
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String trimSlash(String value) { return clean(value).replaceAll("/+$", ""); }
}
