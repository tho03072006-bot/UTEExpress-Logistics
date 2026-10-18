package vn.edu.hcmute.uteexpress.dto.admin;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.admin.AuditEvent;
public record AuditEventResponse(String actor, String action, String target, String detail, LocalDateTime createdAt) {
    public String getActionLabel() {
        return switch (action == null ? "" : action) {
            case "ACCOUNT_CREATE", "STAFF_CREATE" -> "Cấp tài khoản";
            case "ACCOUNT_UPDATE", "STAFF_UPDATE" -> "Cập nhật tài khoản / quyền";
            case "ACCOUNT_DELETE" -> "Xóa tài khoản chưa có liên kết";
            case "ACCOUNT_PASSWORD_RESET", "STAFF_PASSWORD_RESET" -> "Cấp lại mật khẩu";
            case "STAFF_PASSWORD_CHANGE" -> "Đổi mật khẩu cá nhân";
            case "ACCESS_REVOKE" -> "Thu hồi truy cập";
            case "API_TOKEN_ISSUE" -> "Cấp token tích hợp";
            case "ORDER_ASSIGN" -> "Phân công Shipper";
            case "CASE_REQUEST" -> "Lập hồ sơ xử lý";
            case "CASE_APPROVE" -> "Phê duyệt hồ sơ";
            case "CASE_REJECT" -> "Từ chối hồ sơ";
            case "CASE_COMPLETE" -> "Hoàn tất hồ sơ";
            case "RETURN_START" -> "Bắt đầu hoàn hàng";
            case "RETRY_START" -> "Bắt đầu giao lại";
            case "TARIFF_UPDATE" -> "Cập nhật bảng cước";
            case "TARIFF_SCHEDULE" -> "Lập lịch cước";
            case "TARIFF_CANCEL" -> "Hủy lịch cước";
            case "POLICY_UPDATE" -> "Cập nhật chính sách";
            case "PROMOTION_CREATE" -> "Tạo khuyến mãi";
            case "PROMOTION_UPDATE" -> "Cập nhật khuyến mãi";
            case "PROMOTION_STATUS" -> "Đổi trạng thái khuyến mãi";
            default -> action == null || action.isBlank() ? "Chưa xác định" : action;
        };
    }
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getActor(), event.getAction(), event.getTarget(), event.getDetail(), event.getCreatedAt());
    }
}

