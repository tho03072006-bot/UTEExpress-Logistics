package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.EmailChangeRequest;

import java.util.Optional;

public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, Long> {

    Optional<EmailChangeRequest> findByUser(AppUser user);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from EmailChangeRequest r where r.user = :user")
    Optional<EmailChangeRequest> findLockedByUser(@org.springframework.data.repository.query.Param("user") AppUser user);

    void deleteByUser(AppUser user);
}
