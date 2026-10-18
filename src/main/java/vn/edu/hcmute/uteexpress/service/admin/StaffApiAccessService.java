package vn.edu.hcmute.uteexpress.service.admin;

import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.admin.*;

public interface StaffApiAccessService {
    String issueToken(String actor, ApiAccessRequest request);
    Page<StaffAccessResponse> findAccess(String actor, int page);
    void revokeAccess(String actor, Long id, AccessRevocationRequest request);
}
