package vn.edu.hcmute.uteexpress.service.routing;

import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.service.tracking.DispatchService;

/** Mỗi đơn là một giao dịch độc lập; thất bại một đơn không làm mất kết quả các đơn trước. */
@Service
public class DispatchBulkService {
    private final DispatchService dispatch;
    public DispatchBulkService(DispatchService dispatch) { this.dispatch = dispatch; }
    public record Result(int assigned, List<String> remaining) { }
    public Result assign(String actor, Long branchId) {
        var codes = dispatch.unassigned(actor, branchId); int assigned = 0; var remaining = new ArrayList<String>();
        for (var code : codes) {
            try { dispatch.assignSuggested(code, actor); assigned++; }
            catch (IllegalArgumentException | org.springframework.dao.ConcurrencyFailureException ex) {
                remaining.add(code + ": " + (ex instanceof IllegalArgumentException ? ex.getMessage() : "Dữ liệu đang được cập nhật, hãy thử lại."));
            }
        }
        return new Result(assigned, List.copyOf(remaining));
    }
}
