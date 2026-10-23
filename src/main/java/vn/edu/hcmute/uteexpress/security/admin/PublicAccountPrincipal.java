package vn.edu.hcmute.uteexpress.security.admin;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/** Giữ dấu vân tay, không giữ mật khẩu rõ; vẫn so được sau eraseCredentials(). */
public class PublicAccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final Long userId;
    private final long accessVersion;
    private final byte[] passwordFingerprint;
    public PublicAccountPrincipal(AppUser user) {
        super(user.getUsername(), user.getPassword(), user.isEnabled() && !user.isLockedByAdmin(),
            true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        userId = user.getId(); accessVersion = user.getAccessVersion(); passwordFingerprint = fingerprint(user.getPassword());
    }
    public boolean matchesAccount(AppUser user) {
        return Objects.equals(userId, user.getId()) && user.isEnabled() && !user.isLockedByAdmin()
            && accessVersion == user.getAccessVersion()
            && MessageDigest.isEqual(passwordFingerprint, fingerprint(user.getPassword()))
            && (user.getRole() == AppUser.Role.USER || user.getRole() == AppUser.Role.SHIPPER)
            && getAuthorities().contains(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }
    public Long getUserId() { return userId; }
    private static byte[] fingerprint(String hash) {
        try { return MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("Không hỗ trợ SHA-256.", ex); }
    }
}
