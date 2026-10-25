package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.StaffBranch;

/** Truy vấn dữ liệu StaffBranch; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface StaffBranchRepository extends JpaRepository<StaffBranch, Long> {
    boolean existsByAssignedBy_Id(Long assignedById);
    Optional<StaffBranch> findByUser_Id(Long userId);
    /**
     * Điều kiện JPQL cho nhân sự có bí danh {@code u} và tham số {@code :branchId} (null = mọi chi nhánh).
     * Nhân sự chưa được gán chi nhánh hiện cho mọi chi nhánh tới khi chuyển dữ liệu, giống quy tắc của đơn cũ.
     */
    String USER_SCOPE = " (:branchId is null or not exists (select sb0.id from StaffBranch sb0 where sb0.user = u)"
            + " or exists (select sb1.id from StaffBranch sb1 where sb1.user = u and sb1.branch.id = :branchId)) ";
    List<StaffBranch> findAllByBranch_Id(Long branchId);
    /** Nhân sự của nhiều chi nhánh kèm tài khoản trong một truy vấn. */
    @org.springframework.data.jpa.repository.Query("select s from StaffBranch s join fetch s.user where s.branch.id in :ids")
    List<StaffBranch> findWithUserByBranchIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
