package vn.edu.hcmute.uteexpress.service.admin;

import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.admin.*;

public interface StaffAccountService {
    Page<StaffAccountResponse> findAccounts(String actor, int page);
    void createAccount(String actor, StaffAccountCreateRequest request);
    void updateAccount(String actor, Long id, StaffAccountUpdateRequest request);
    void resetPassword(String actor, Long id, long revision, StaffPasswordRequest request);
    void changePassword(String actor, StaffPasswordChangeRequest request);
}

