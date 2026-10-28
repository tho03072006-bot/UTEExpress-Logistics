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
    @Query("""
        select coalesce(sum(case when s.totalAmount > s.declaredAmount then s.totalAmount - s.declaredAmount else 0 end), 0)
        from CodSettlement s where s.shipper = :shipper and s.status = vn.edu.hcmute.uteexpress.entity.CodSettlement.Status.CONFIRMED
        """)
    BigDecimal sumConfirmedShortage(@Param("shipper") AppUser shipper);

    @Query("""
        select coalesce(sum(case when s.declaredAmount > s.totalAmount then s.declaredAmount - s.totalAmount else 0 end), 0)
        from CodSettlement s where s.shipper = :shipper and s.status = vn.edu.hcmute.uteexpress.entity.CodSettlement.Status.CONFIRMED
        """)
    BigDecimal sumConfirmedExcess(@Param("shipper") AppUser shipper);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CodSettlement s where s.id = :id")
    Optional<CodSettlement> lockById(@Param("id") Long id);
    boolean existsByShipperAndStatus(AppUser shipper, CodSettlement.Status status);

    Optional<CodSettlement> findFirstByShipperAndStatusOrderByIdDesc(AppUser shipper, CodSettlement.Status status);

    Page<CodSettlement> findByShipper(AppUser shipper, Pageable pageable);

    /** Phiếu của Shipper thuộc chi nhánh (xem StaffBranchRepository.USER_SCOPE); branchId null là mọi chi nhánh. */
    @Query(value = "select s from CodSettlement s join fetch s.shipper u where (:status is null or s.status = :status) and" + vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository.USER_SCOPE,
            countQuery = "select count(s) from CodSettlement s join s.shipper u where (:status is null or s.status = :status) and" + vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository.USER_SCOPE)
    Page<CodSettlement> search(@Param("status") CodSettlement.Status status, @Param("branchId") Long branchId, Pageable pageable);

    @Query("select count(s) from CodSettlement s join s.shipper u where s.status = :status and" + vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository.USER_SCOPE)
    long countByStatus(@Param("status") CodSettlement.Status status, @Param("branchId") Long branchId);

    @Query("select sum(s.declaredAmount) from CodSettlement s join s.shipper u where s.status = :status and" + vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository.USER_SCOPE)
    BigDecimal sumDeclared(@Param("status") CodSettlement.Status status, @Param("branchId") Long branchId);

    /** Khóa dòng tài khoản Shipper trong lúc tạo phiếu để hai yêu cầu song song không cùng tạo phiếu chờ duyệt. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockShipper(@Param("id") Long id);
}
