package vn.edu.hcmute.uteexpress.service.manager;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
public interface OperationsCaseService {
    byte[] readEvidence(String actor, Long id);
    /** Manager: hồ sơ của chi nhánh mình; Admin: mọi chi nhánh hoặc chi nhánh đã chọn. "Chờ duyệt" đứng đầu. */
    Page<OperationsCaseResponse> list(String actor, OperationsCase.Kind kind, OperationsCase.Status status, Long branchId, int page);
    default Page<OperationsCaseResponse> list(String actor, OperationsCase.Kind kind, int page) { return list(actor, kind, null, null, page); }
    java.util.List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor);
    OperationsCase view(String actor, Long id);
    /** Nút hoàn hàng theo đúng kho và giai đoạn; các thao tác ghi vẫn kiểm tra lại quyền. */
    record ReturnActions(boolean canStart, boolean canComplete) { }
    ReturnActions returnActions(String actor, Long id);
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
