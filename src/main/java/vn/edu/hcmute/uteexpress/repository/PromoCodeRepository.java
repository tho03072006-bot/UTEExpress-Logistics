package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.edu.hcmute.uteexpress.entity.PromoCode;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    /** Ma luu dang chu hoa nhung nguoi dung hay go chu thuong, nen tra khong phan biet hoa/thuong. */
    Optional<PromoCode> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCode p where lower(p.code) = lower(:code)")
    Optional<PromoCode> findByCodeIgnoreCaseForUpdate(@Param("code") String code);
}
