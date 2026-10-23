package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, AppUserLookup {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockById(Long id);

    org.springframework.data.domain.Page<AppUser> findByRoleAndEnabledTrue(
            AppUser.Role role, org.springframework.data.domain.Pageable pageable);

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.username = :username")
    Optional<AppUser> findLockedByUsername(@org.springframework.data.repository.query.Param("username") String username);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findLockedByEmail(@org.springframework.data.repository.query.Param("email") String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Dem khach hang da kich hoat, dung cho khoi so lieu o trang chu. */
    long countByRoleAndEnabledTrue(AppUser.Role role);
}
