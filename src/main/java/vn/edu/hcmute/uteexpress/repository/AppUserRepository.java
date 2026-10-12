package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.edu.hcmute.uteexpress.entity.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    /** Serialize OTP consumption and per-owner address quotas inside the caller's transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.username = :username")
    Optional<AppUser> findByUsernameForUpdate(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.email = :email")
    Optional<AppUser> findByEmailForUpdate(@Param("email") String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Dem khach hang da kich hoat, dung cho khoi so lieu o trang chu. */
    long countByRoleAndEnabledTrue(AppUser.Role role);
}
