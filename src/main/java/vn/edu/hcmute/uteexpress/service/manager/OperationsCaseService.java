package vn.edu.hcmute.uteexpress.service.manager;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
public interface OperationsCaseService {
    Page<OperationsCaseResponse> list(String actor, OperationsCase.Kind kind, int page);
    OperationsCase view(String actor, Long id);
    boolean requiresAdminApproval(String actor, Long id);
    OperationsCaseRequest prepare(String actor, OperationsCase.Kind kind, Long targetId);
    Long request(String actor, OperationsCaseRequest form, MultipartFile evidence);
    void review(String actor, Long id, OperationsReviewRequest form);
    void startReturn(String actor, Long id, long revision, String reason);
    void complete(String actor, Long id, long revision, String reference, MultipartFile evidence);
    BigDecimal outstanding(CodSettlement settlement);
    BigDecimal refundable(CodSettlement settlement);
    boolean canRetry(Order order);
    void beforeStatusChange(Order order, String shipper, Order.OrderStatus status);
}
