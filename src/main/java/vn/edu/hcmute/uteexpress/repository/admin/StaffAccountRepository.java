package vn.edu.hcmute.uteexpress.repository.admin;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<StaffAccount> findByUserUsername(String username);

    @EntityGraph(attributePaths = "user")
    org.springframework.data.domain.Page<StaffAccount> findAll(org.springframework.data.domain.Pageable pageable);

    // Khóa theo cùng thứ tự để hai Admin không đồng thời khóa/hạ quyền nhau.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StaffAccount s order by s.id")
    List<StaffAccount> lockAccounts();
}
