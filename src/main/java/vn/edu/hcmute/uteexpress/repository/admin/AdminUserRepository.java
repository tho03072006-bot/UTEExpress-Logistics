package vn.edu.hcmute.uteexpress.repository.admin;

import java.util.Optional;
import org.springframework.data.repository.Repository;
import vn.edu.hcmute.uteexpress.entity.AppUser;

public interface AdminUserRepository extends Repository<AppUser, Long> {
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    AppUser save(AppUser user);
}
