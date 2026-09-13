package vn.edu.hcmute.uteexpress.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.PromoCode;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    /** Ma luu dang chu hoa nhung nguoi dung hay go chu thuong, nen tra khong phan biet hoa/thuong. */
    Optional<PromoCode> findByCodeIgnoreCase(String code);
}
