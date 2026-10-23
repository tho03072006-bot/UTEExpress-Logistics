package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderReadRepository;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccessPolicy;
import vn.edu.hcmute.uteexpress.service.manager.*;

@Service
@Transactional(readOnly = true)
public class StaffWorkbenchServiceImpl implements StaffWorkbenchService {
    private final StaffAccessPolicy access;
    private final OperationsOverviewService overview;
    private final ManagerOrderReadRepository orders;
    private final vn.edu.hcmute.uteexpress.service.routing.BranchScope scope;

    public StaffWorkbenchServiceImpl(StaffAccessPolicy access, OperationsOverviewService overview,
            ManagerOrderReadRepository orders, vn.edu.hcmute.uteexpress.service.routing.BranchScope scope) {
        this.access = access; this.overview = overview; this.orders = orders; this.scope = scope;
    }

    public List<WorkQueueResponse> getWorkQueues(String actor) {
        access.requireOperator(actor);
        return List.of(
            createQueue(actor, OperationsOverviewService.Alert.UNASSIGNED, "Chưa có Shipper", "Phân công sau khi kiểm tra thanh toán."),
            createQueue(actor, OperationsOverviewService.Alert.LATE_PICKUP, "Chậm lấy hàng", "Theo thời hạn trong chính sách vận hành."),
            createQueue(actor, OperationsOverviewService.Alert.PAYMENT_ISSUE, "Cần kiểm tra thanh toán", "Không tự đánh dấu ví đã trả tiền."),
            createQueue(actor, OperationsOverviewService.Alert.LATE_DELIVERY, "Chậm giao hàng", "Đối chiếu dịch vụ và trạng thái thực tế."),
            createQueue(actor, OperationsOverviewService.Alert.RETRY_DUE, "Đến lịch giao lại", "Chỉ các hồ sơ đã đủ điều kiện."),
            new WorkQueueResponse("Khiếu nại quá hạn", overview.lateComplaints(actor, 0).getTotalElements(),
                "/manager/canh-bao#khieu-nai-qua-han", "Ưu tiên phản hồi và xử lý theo chính sách."));
    }

    private WorkQueueResponse createQueue(String actor, OperationsOverviewService.Alert alert, String label, String description) {
        return new WorkQueueResponse(label, overview.alerts(actor, alert, 0).getTotalElements(),
                "/manager/canh-bao?alert=" + alert.name(), description);
    }

    public List<BatchTrackingResponse> findShipments(String actor, BatchTrackingRequest request) {
        access.requireOperator(actor); access.validate(request);
        String raw = request.getCodes().strip();
        // Chặn phần tử rỗng và giới hạn TRƯỚC khi gộp trùng để không lách giới hạn 10 mã.
        String[] parts = raw.split("(?:\\r\\n|[\\r\\n,;])", -1);
        if (parts.length > 10) throw new IllegalArgumentException("Mỗi lần chỉ tra cứu tối đa 10 mã vận đơn.");
        Set<String> codes = new LinkedHashSet<>();
        for (String part : parts) {
            String code = part.strip().toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z0-9-]{1,20}"))
                throw new IllegalArgumentException("Mỗi mã từ 1–20 ký tự chữ, số hoặc gạch ngang; không để mục trống.");
            codes.add(code);
        }
        var found = orders.findByTrackingCodeIn(codes).stream().collect(Collectors.toMap(
                order -> order.getTrackingCode().toUpperCase(Locale.ROOT), Function.identity()));
        // Manager chỉ xem đơn gửi đi hoặc giao tới chi nhánh mình; đơn khác chỉ báo thuộc chi nhánh khác.
        var visible = scope.visible(actor, found.values());
        return codes.stream().map(code -> {
            Order order = found.get(code);
            if (order == null) return new BatchTrackingResponse(code, false, "Không tìm thấy", "", null, null, false);
            if (!visible.contains(order.getId())) return new BatchTrackingResponse(code, false, "Thuộc chi nhánh khác", "", null, null, false);
            return new BatchTrackingResponse(code, true,
                order.getStatus() == null ? "Chưa xác định" : ManagerOrderResponse.getStatusLabels().getOrDefault(order.getStatus(), "Chưa xác định"),
                order.getServiceType() == null ? "Chưa xác định" : order.getServiceType().name(),
                order.getShippingFee(), order.getCreatedAt(), order.getShipper() != null);
        }).toList();
    }

    public String exportShipments(String actor, BatchTrackingRequest request) {
        StringBuilder csv = new StringBuilder("\uFEFFMa van don,Trang thai,Dich vu,Cuoc VND,Ngay tao,Phan cong\r\n");
        for (var row : findShipments(actor, request)) {
            csv.append(escapeCell(row.trackingCode())).append(',').append(escapeCell(row.status())).append(',')
                .append(escapeCell(row.serviceType())).append(',')
                .append(row.shippingFee() == null ? "" : escapeCell(row.shippingFee().toPlainString())).append(',')
                .append(escapeCell(row.createdAt() == null ? "" : row.createdAt().toString())).append(',')
                .append(escapeCell(!row.found() ? "" : row.assigned() ? "Da phan cong" : "Chua phan cong")).append("\r\n");
        }
        return csv.toString();
    }

    private static String escapeCell(String value) {
        String safe = value == null ? "" : value;
        if (safe.stripLeading().matches("^[=+@\\-].*")) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
