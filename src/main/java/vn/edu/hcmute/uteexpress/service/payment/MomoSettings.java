package vn.edu.hcmute.uteexpress.service.payment;

import java.net.URI;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;

/** Cấu hình dùng chung giữa web local và simulator; không có khóa mặc định. */
public final class MomoSettings {
    private final Environment environment;
    public MomoSettings(Environment environment) { this.environment = environment; }

    public SecretKeySpec secretKey() {
        String value = environment.getProperty("MOMO_SIM_SECRET",
                environment.getProperty("app.momo.sim-secret", ""));
        try {
            byte[] bytes = Base64.getDecoder().decode(value.strip());
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Chưa cấu hình khóa MoMo mô phỏng 32 byte (Base64). Liên hệ quản trị viên.");
        }
    }

    public String baseUrl() {
        String value = environment.getProperty("MOMO_SIMULATOR_URL",
                environment.getProperty("app.momo.simulator-url", "")).strip();
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || !uri.getHost().matches("[a-z0-9][a-z0-9-]*\\.onrender\\.com")
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null
                    || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw new IllegalArgumentException();
            }
            return "https://" + uri.getHost();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Chưa cấu hình đúng địa chỉ HTTPS *.onrender.com của web MoMo mô phỏng.");
        }
    }
}
