package vn.edu.hcmute.uteexpress.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.time.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    static final String TEST_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    static final Clock NOW = Clock.fixed(Instant.parse("2026-10-18T06:00:00Z"), ZoneOffset.UTC);
    private JwtTokenProvider tokens;
    private StaffAccount account;
    @BeforeEach void setUp() {
        tokens = new JwtTokenProvider(true, TEST_KEY, NOW);
        account = new StaffAccount(); account.setId(2L); account.setPasswordChangeRequired(false);
        var user = new AppUser("manager01", "not-a-token-claim", "manager@example.com");
        user.setId(3L); user.setRole(AppUser.Role.MANAGER); user.setEnabled(true); account.setUser(user);
    }
    @Test void tokenHasStrictClaimsAndNoPasswordOrContactData() {
        var claims = tokens.parseToken(tokens.generateToken(account));
        assertTrue(tokens.matchesAccount(claims, account));
        assertEquals("operations:read", claims.get("scope"));
        assertEquals(1800000, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertFalse(claims.toString().contains("not-a-token-claim")); assertFalse(claims.toString().contains("@"));
    }
    @Test void expiredTokenIsRejected() {
        String token = tokens.generateToken(account);
        var later = new JwtTokenProvider(true, TEST_KEY, Clock.offset(NOW, Duration.ofSeconds(1800)));
        assertThrows(JwtException.class, () -> later.parseToken(token));
    }
    @Test void changedStaffVersionRevokesToken() {
        var claims = tokens.parseToken(tokens.generateToken(account)); account.revokeSessions();
        assertFalse(tokens.matchesAccount(claims, account));
    }
    @Test void changedUserVersionRevokesToken() {
        var claims = tokens.parseToken(tokens.generateToken(account)); account.getUser().revokeSessions();
        assertFalse(tokens.matchesAccount(claims, account));
    }
    @Test void changedRoleRevokesTokenEvenWithoutVersionChange() {
        var claims = tokens.parseToken(tokens.generateToken(account)); account.getUser().setRole(AppUser.Role.ADMIN);
        assertFalse(tokens.matchesAccount(claims, account));
    }
    @Test void lockedDisabledMissingOrPasswordChangeRequiredAccountIsRejected() {
        var claims = tokens.parseToken(tokens.generateToken(account));
        account.getUser().setLockedByAdmin(true); assertFalse(tokens.matchesAccount(claims, account));
        account.getUser().setLockedByAdmin(false); account.getUser().setEnabled(false); assertFalse(tokens.matchesAccount(claims, account));
        account.getUser().setEnabled(true); account.setActive(false); assertFalse(tokens.matchesAccount(claims, account));
        account.setActive(true); account.setPasswordChangeRequired(true); assertFalse(tokens.matchesAccount(claims, account));
        assertFalse(tokens.matchesAccount(claims, null)); assertThrows(IllegalArgumentException.class, () -> tokens.generateToken(account));
    }
    @ParameterizedTest @ValueSource(strings = {"", "not-base64", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "c2hvcnQ="})
    void enabledJwtRejectsInvalidOrWeakKey(String key) { assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(true, key, NOW)); }
    @Test void disabledJwtDoesNotBreakWebButCannotIssueOrParseTokens() {
        var disabled = new JwtTokenProvider(false, "", NOW); assertFalse(disabled.isEnabled());
        assertThrows(IllegalStateException.class, () -> disabled.generateToken(account));
        assertThrows(IllegalStateException.class, () -> disabled.parseToken("anything"));
    }
    @ParameterizedTest @ValueSource(ints = {0, -1, 14, 61, 10000})
    void unreasonableLifetimeIsRejected(int minutes) {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(true, TEST_KEY, minutes, NOW));
    }
    @ParameterizedTest @ValueSource(ints = {15, 30, 60})
    void configuredLifetimeHasExactExpiry(int minutes) {
        var provider = new JwtTokenProvider(true, TEST_KEY, minutes, NOW);
        var claims = provider.parseToken(provider.generateToken(account));
        assertEquals(minutes * 60000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
    }
    @ParameterizedTest @ValueSource(strings = {"iss", "aud", "sub", "jti", "iat", "exp", "nbf", "uid", "role", "av", "uv", "scope"})
    void missingRequiredClaimIsRejected(String missing) {
        var claims = new HashMap<String, Object>(tokens.parseToken(tokens.generateToken(account)));
        claims.remove(missing); assertThrows(JwtException.class, () -> tokens.parseToken(sign(claims)));
    }
    @ParameterizedTest @ValueSource(strings = {"iss", "aud", "sub", "uid", "role", "scope", "jti", "av", "uv"})
    void invalidRequiredClaimIsRejected(String field) {
        var claims = new HashMap<String, Object>(tokens.parseToken(tokens.generateToken(account)));
        claims.put(field, "invalid value"); assertThrows(JwtException.class, () -> tokens.parseToken(sign(claims)));
    }
    @Test void futureIssueOrOverlongExpiryIsRejected() {
        var claims = new HashMap<String, Object>(tokens.parseToken(tokens.generateToken(account)));
        claims.put("exp", Date.from(NOW.instant().plusSeconds(3600))); assertThrows(JwtException.class, () -> tokens.parseToken(sign(claims)));
        claims.put("iat", Date.from(NOW.instant().plusSeconds(60))); assertThrows(JwtException.class, () -> tokens.parseToken(sign(claims)));
    }
    @Test void wrongSignatureUnsignedWrongTypeAndOverlongInputAreRejected() {
        String token = tokens.generateToken(account);
        var other = new JwtTokenProvider(true, Base64.getEncoder().encodeToString(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32}), NOW);
        assertThrows(JwtException.class, () -> other.parseToken(token));
        assertThrows(JwtException.class, () -> tokens.parseToken(Jwts.builder().setSubject("manager01").compact()));
        assertThrows(JwtException.class, () -> tokens.parseToken("a".repeat(4097)));
        var claims = tokens.parseToken(token);
        var wrongType = Jwts.builder().setHeaderParam("typ", "JWT").setClaims(claims)
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_KEY)), SignatureAlgorithm.HS256).compact();
        assertThrows(JwtException.class, () -> tokens.parseToken(wrongType));
    }
    private String sign(Map<String,Object> claims) {
        return Jwts.builder().setHeaderParam("typ", "at+jwt").setClaims(claims)
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_KEY)), SignatureAlgorithm.HS256).compact();
    }
}
