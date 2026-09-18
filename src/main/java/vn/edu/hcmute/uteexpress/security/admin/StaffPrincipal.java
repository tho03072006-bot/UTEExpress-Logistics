package vn.edu.hcmute.uteexpress.security.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;

public class StaffPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final long accessVersion;
    private final String passwordFingerprint;

    public StaffPrincipal(StaffAccount account) {
        super(account.getUser().getUsername(), account.getUser().getPassword(),
                account.isActive() && account.getUser().isEnabled(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getUser().getRole().name())));
        accessVersion = account.getAccessVersion();
        passwordFingerprint = fingerprint(account.getUser().getPassword());
    }

    public boolean matchesAccount(StaffAccount account) {
        return account.isActive() && account.getUser().isEnabled()
                && accessVersion == account.getAccessVersion()
                && passwordFingerprint.equals(fingerprint(account.getUser().getPassword()))
                && getAuthorities().contains(new SimpleGrantedAuthority("ROLE_" + account.getUser().getRole().name()));
    }

    private static String fingerprint(String passwordHash) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Không hỗ trợ SHA-256.", ex);
        }
    }
}

