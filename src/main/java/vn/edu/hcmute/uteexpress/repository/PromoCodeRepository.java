package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.PromoCode;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PromoCode p where lower(p.code) = lower(:code)")
    Optional<PromoCode> lockByCode(@org.springframework.data.repository.query.Param("code") String code);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PromoCode p where p.id = :id")
    Optional<PromoCode> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    /** Ma luu dang chu hoa nhung nguoi dung hay go chu thuong, nen tra khong phan biet hoa/thuong. */
    Optional<PromoCode> findByCodeIgnoreCase(String code);
}
