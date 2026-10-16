package vn.edu.hcmute.uteexpress.service.admin;

import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.dto.admin.*;

public interface AdminAccountService {
    Page<AdminAccountResponse> findAccounts(String actor, AppUser.Role role, int page);
    AdminAccountResponse findAccount(String actor, Long id);
    void createAccount(String actor, AdminAccountCreateRequest form);
    void updateAccount(String actor, Long id, AdminAccountUpdateRequest form);
    void deleteAccount(String actor, Long id, AdminAccountDeleteRequest form);
    void resetPassword(String actor, Long id, AdminAccountPasswordRequest form);
}
