package vn.edu.hcmute.uteexpress.repository.manager;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.AppUser;
public interface ManagerShipperRepository extends org.springframework.data.repository.Repository<AppUser, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockShipper(@Param("id") Long id);
}

