package vn.edu.hcmute.uteexpress.service.admin;
import vn.edu.hcmute.uteexpress.dto.admin.OperationsPolicyRequest;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy;
public interface OperationsPolicyService {
    OperationsPolicy current();
    void update(String actor, OperationsPolicyRequest request);
}
