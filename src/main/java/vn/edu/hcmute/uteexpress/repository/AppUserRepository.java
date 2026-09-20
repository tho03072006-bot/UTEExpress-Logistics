package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Dem khach hang da kich hoat, dung cho khoi so lieu o trang chu. */
    long countByRoleAndEnabledTrue(AppUser.Role role);
}
