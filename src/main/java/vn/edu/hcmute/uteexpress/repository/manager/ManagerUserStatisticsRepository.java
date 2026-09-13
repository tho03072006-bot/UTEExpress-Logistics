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
}
