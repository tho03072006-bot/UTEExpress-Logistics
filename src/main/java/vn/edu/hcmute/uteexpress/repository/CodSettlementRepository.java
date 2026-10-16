package vn.edu.hcmute.uteexpress.repository;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.CodSettlement;

public interface CodSettlementRepository extends JpaRepository<CodSettlement, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CodSettlement s where s.id = :id")
    Optional<CodSettlement> lockById(@Param("id") Long id);
    boolean existsByShipperAndStatus(AppUser shipper, CodSettlement.Status status);

    Optional<CodSettlement> findFirstByShipperAndStatusOrderByIdDesc(AppUser shipper, CodSettlement.Status status);

    Page<CodSettlement> findByShipper(AppUser shipper, Pageable pageable);

    @Query(value = "select s from CodSettlement s join fetch s.shipper where (:status is null or s.status = :status)",
            countQuery = "select count(s) from CodSettlement s where (:status is null or s.status = :status)")
    Page<CodSettlement> search(@Param("status") CodSettlement.Status status, Pageable pageable);

    long countByStatus(CodSettlement.Status status);

    @Query("select sum(s.declaredAmount) from CodSettlement s where s.status = :status")
    BigDecimal sumDeclared(@Param("status") CodSettlement.Status status);

    /** Khóa dòng tài khoản Shipper trong lúc tạo phiếu để hai yêu cầu song song không cùng tạo phiếu chờ duyệt. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockShipper(@Param("id") Long id);
}
