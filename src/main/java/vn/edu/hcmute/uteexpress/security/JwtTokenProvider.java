package vn.edu.hcmute.uteexpress.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;

/** JWT chỉ dành cho API nội bộ chỉ đọc, không thay thế phiên web/CSRF. */
@Component
public class JwtTokenProvider {
    public static final int DEFAULT_LIFETIME_MINUTES = 30;
    private static final String ISSUER = "uteexpress";
    private static final String AUDIENCE = "uteexpress-staff-api";
    private final SecretKey key;
    private final Clock clock;
    private final long lifetimeSeconds;

    @Autowired
    public JwtTokenProvider(@Value("${APP_JWT_ENABLED:${app.jwt.api.enabled:false}}") boolean enabled,
            @Value("${APP_JWT_SIGNING_KEY:${app.jwt.api.signing-key:}}") String encodedKey,
            @Value("${APP_JWT_TTL_MINUTES:${app.jwt.api.ttl-minutes:30}}") int lifetimeMinutes, Clock clock) {
        this.clock = clock;
        if (lifetimeMinutes < 15 || lifetimeMinutes > 60)
            throw new IllegalStateException("Thời hạn JWT phải từ 15 đến 60 phút.");
        lifetimeSeconds = lifetimeMinutes * 60L;
        SecretKey configured = null;
        if (enabled) {
            try {
                byte[] bytes = Base64.getDecoder().decode(encodedKey);
                if (bytes.length != 32 || java.util.Arrays.equals(bytes, new byte[32]))
                    throw new IllegalArgumentException();
                configured = Keys.hmacShaKeyFor(bytes);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("JWT cần khóa Base64 ngẫu nhiên đúng 32 byte; không dùng khóa mẫu.", ex);
            }
        }
        key = configured;
    }

    public JwtTokenProvider(boolean enabled, String encodedKey, Clock clock) {
        this(enabled, encodedKey, DEFAULT_LIFETIME_MINUTES, clock);
    }

    public boolean isEnabled() { return key != null; }
    public long getLifetimeSeconds() { return lifetimeSeconds; }

    public String generateToken(StaffAccount account) {
        requireEnabled();
        if (!isEligible(account)) throw new IllegalArgumentException("Tài khoản chưa đủ điều kiện dùng API.");
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return Jwts.builder().setHeaderParam("typ", "at+jwt")
            .setIssuer(ISSUER).setAudience(AUDIENCE).setSubject(account.getUser().getUsername())
            .setId(UUID.randomUUID().toString()).setIssuedAt(Date.from(now)).setNotBefore(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(lifetimeSeconds)))
            .claim("uid", account.getUser().getId().toString())
            .claim("role", account.getUser().getRole().name())
            .claim("av", account.getAccessVersion()).claim("uv", account.getUser().getAccessVersion())
            .claim("scope", "operations:read").signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public Claims parseToken(String token) {
        requireEnabled();
        if (token == null || token.length() > 4096 || token.isBlank()) throw invalid();
        var parsed = Jwts.parserBuilder().setSigningKey(key).requireIssuer(ISSUER).requireAudience(AUDIENCE)
            .require("scope", "operations:read").setClock(() -> Date.from(clock.instant()))
            .build().parseClaimsJws(token);
        Claims claims = parsed.getBody();
        Date issued = claims.getIssuedAt(), expires = claims.getExpiration(), notBefore = claims.getNotBefore();
        Instant now = clock.instant();
        if (!"HS256".equals(parsed.getHeader().getAlgorithm()) || !"at+jwt".equals(parsed.getHeader().getType())
                || issued == null || expires == null || notBefore == null || !issued.equals(notBefore)
                || issued.toInstant().isAfter(now) || !expires.toInstant().isAfter(now)
                || !expires.after(issued) || java.time.Duration.between(issued.toInstant(), expires.toInstant())
                    .compareTo(java.time.Duration.ofSeconds(lifetimeSeconds)) > 0
                || claims.getSubject() == null || !claims.getSubject().matches("[A-Za-z0-9_.-]{3,50}")
                || claims.getId() == null || !claims.getId().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || !(claims.get("uid") instanceof String uid) || !uid.matches("[1-9][0-9]{0,18}")
                || !("ADMIN".equals(claims.get("role")) || "MANAGER".equals(claims.get("role")))
                || !isVersion(claims.get("av")) || !isVersion(claims.get("uv"))) throw invalid();
        return claims;
    }

    public boolean matchesAccount(Claims claims, StaffAccount account) {
        return isEligible(account) && account.getUser().getUsername().equals(claims.getSubject())
            && account.getUser().getId().toString().equals(claims.get("uid"))
            && account.getUser().getRole().name().equals(claims.get("role"))
            && account.getAccessVersion() == ((Number) claims.get("av")).longValue()
            && account.getUser().getAccessVersion() == ((Number) claims.get("uv")).longValue();
    }

    private static boolean isVersion(Object value) {
        return (value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= 0;
    }
    private static boolean isEligible(StaffAccount account) {
        return account != null && account.getUser() != null && account.getUser().getId() != null
            && account.isActive() && account.getUser().isEnabled() && !account.getUser().isLockedByAdmin()
            && !account.isPasswordChangeRequired()
            && (account.getUser().getRole() == vn.edu.hcmute.uteexpress.entity.AppUser.Role.ADMIN
                || account.getUser().getRole() == vn.edu.hcmute.uteexpress.entity.AppUser.Role.MANAGER);
    }
    private void requireEnabled() {
        if (key == null) throw new IllegalStateException("API JWT chưa được bật bằng khóa riêng an toàn.");
    }
    private static JwtException invalid() { return new JwtException("Token không hợp lệ."); }
}
