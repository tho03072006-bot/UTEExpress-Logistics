package vn.edu.hcmute.uteexpress.service;

import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.config.MomoProperties;
import vn.edu.hcmute.uteexpress.dto.MomoCheckout;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentAttempt;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentResult;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Signed IPN + server status query. Browser-return alone can never settle a shipping fee. */
@Service
public class MomoPaymentService {
    private static final Pattern REFERENCE = Pattern.compile("^UTE-([1-9][0-9]{0,18})-[QH]-[0-9a-f]{4,16}$");
    private static final Set<String> PENDING = Set.of("1000", "7000", "7002", "9000");
    private final MomoProperties properties;
    private final MomoApiClient apiClient;
    private final OrderPaymentService paymentService;

    public MomoPaymentService(MomoProperties properties, MomoApiClient apiClient, OrderPaymentService paymentService) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.paymentService = paymentService;
    }

    public boolean isEnabled() { return properties.isEnabled(); }

    public MomoCheckout start(Long orderId, String username, boolean qr) {
        properties.requireEnabled();
        // Reserving commits before HTTP; timeouts cannot discard the correlation ID.
        MomoPaymentAttempt attempt = paymentService.reserveMomoAttempt(orderId, username, qr);
        return apiClient.create(attempt, qr);
    }

    public MomoPaymentResult check(Long orderId, String username) {
        properties.requireEnabled();
        MomoPaymentAttempt attempt = paymentService.findOwnedMomoAttempt(orderId, username);
        if (attempt.paid()) return result(orderId, MomoPaymentResult.State.PAID);
        Map<String, String> response = apiClient.query(attempt);
        if ("0".equals(response.get("resultCode"))) {
            String transId = MomoApiClient.value(response, "transId");
            MomoApiClient.positiveLong(transId, "Mã giao dịch MoMo không hợp lệ.");
            paymentService.settleVerifiedMomo(orderId, attempt.reference(), attempt.amount(), transId);
            return result(orderId, MomoPaymentResult.State.PAID);
        }
        return result(orderId, PENDING.contains(response.get("resultCode"))
                ? MomoPaymentResult.State.PENDING : MomoPaymentResult.State.FAILED);
    }

    public MomoPaymentResult handleIpn(Map<String, String> params) {
        verifyCallback(params);
        String reference = MomoApiClient.value(params, "orderId");
        Long orderId = orderIdFromReference(reference);
        long amount = MomoApiClient.positiveLong(MomoApiClient.value(params, "amount"), "Số tiền MoMo không hợp lệ.");
        String code = MomoApiClient.value(params, "resultCode");
        if ("0".equals(code)) {
            String transId = MomoApiClient.value(params, "transId");
            MomoApiClient.positiveLong(transId, "Mã giao dịch MoMo không hợp lệ.");
            paymentService.settleVerifiedMomo(orderId, reference, amount, transId);
            return result(orderId, MomoPaymentResult.State.PAID);
        }
        // Pending / failure callbacks never downgrade PAID or mutate a payment record.
        return result(orderId, PENDING.contains(code) ? MomoPaymentResult.State.PENDING : MomoPaymentResult.State.FAILED);
    }

    public void verifyCallback(Map<String, String> params) {
        properties.requireEnabled();
        String reference = MomoApiClient.value(params, "orderId");
        orderIdFromReference(reference);
        apiClient.verifyIdentity(params, reference, reference);
        if (!MomoApiClient.value(params, "extraData").isEmpty()) {
            throw new IllegalStateException("Dữ liệu giao dịch MoMo không hợp lệ.");
        }
        String code = MomoApiClient.value(params, "resultCode");
        if (!code.matches("[0-9]{1,5}")) throw new IllegalStateException("Trạng thái MoMo không hợp lệ.");
        MomoApiClient.positiveLong(MomoApiClient.value(params, "responseTime"), "Thời gian phản hồi MoMo không hợp lệ.");
        String raw = callbackRawSignature(properties.getAccessKey(), params);
        if (!properties.matchesSignature(raw, params.get("signature"))) {
            throw new IllegalStateException("Kết quả MoMo có chữ ký không hợp lệ. Chưa xác nhận thanh toán.");
        }
    }

    public Long orderIdFromReference(String reference) {
        Matcher matcher = REFERENCE.matcher(reference == null ? "" : reference);
        if (!matcher.matches()) throw new IllegalStateException("Mã giao dịch không thuộc UTEExpress.");
        return MomoApiClient.positiveLong(matcher.group(1), "Mã vận đơn MoMo không hợp lệ.");
    }

    public static String callbackRawSignature(String accessKey, Map<String, String> params) {
        StringBuilder raw = new StringBuilder("accessKey=").append(accessKey);
        for (String field : new String[]{"amount", "extraData", "message", "orderId", "orderInfo", "orderType",
                "partnerCode", "payType", "requestId", "responseTime", "resultCode", "transId"}) {
            raw.append('&').append(field).append('=').append(MomoApiClient.value(params, field));
        }
        return raw.toString();
    }

    private MomoPaymentResult result(Long orderId, MomoPaymentResult.State state) {
        return new MomoPaymentResult(orderId, state, switch (state) {
            case PAID -> "MoMo đã xác nhận thanh toán cước thành công.";
            case PENDING -> "Đang chờ xác nhận từ MoMo. Vui lòng hoàn tất thanh toán.";
            case FAILED -> "MoMo chưa xác nhận giao dịch thành công. Cước vẫn chưa thanh toán.";
        });
    }
}
