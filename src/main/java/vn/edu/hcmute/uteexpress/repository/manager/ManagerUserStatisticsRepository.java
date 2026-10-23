package vn.edu.hcmute.uteexpress.repository.manager;

import org.springframework.data.repository.Repository;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/**
 * Repository chi doc so lieu tai khoan cho phan Manager/Admin cua TV3.
 * Tach rieng de khong sua AppUserRepository ma cac thanh vien khac dang su dung.
 */
public interface ManagerUserStatisticsRepository extends Repository<AppUser, Long> {

    long count();

    long countByRole(AppUser.Role role);
    /** Shipper thuộc chi nhánh (Shipper chưa gán chi nhánh tính cho mọi chi nhánh, xem StaffBranchRepository.USER_SCOPE). */
    @org.springframework.data.jpa.repository.Query("select count(u) from AppUser u where u.role = vn.edu.hcmute.uteexpress.entity.AppUser.Role.SHIPPER and"
            + vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository.USER_SCOPE)
    long countShippersInScope(@org.springframework.data.repository.query.Param("branchId") Long branchId);
}
