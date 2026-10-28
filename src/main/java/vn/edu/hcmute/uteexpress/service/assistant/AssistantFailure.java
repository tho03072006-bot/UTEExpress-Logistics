package vn.edu.hcmute.uteexpress.service.assistant;

/** Không bọc exception của nhà cung cấp vì có thể chứa khóa, prompt hoặc dữ liệu cá nhân. */
public final class AssistantFailure extends RuntimeException {
    private final String code;
    private final int status;
    private final long retryAfter;
    public AssistantFailure(String code,int status,long retryAfter) {
        super(switch (code) {
            case "NOT_CONFIGURED" -> "Trợ lý AI chưa được cấu hình hoặc cấu hình chưa hợp lệ. Vui lòng liên hệ quản trị.";
            case "RATE_LIMIT", "ACCOUNT_RATE_LIMIT" -> "Trợ lý AI đang giới hạn lượt gửi. Hãy chờ hết thời gian đếm ngược rồi thử lại.";
            case "QUOTA" -> "Hạn mức AI tạm thời đã hết. Vui lòng chờ hoặc nhờ quản trị kiểm tra hạn mức, không bấm thử lại liên tục.";
            case "BUSY" -> "Một câu hỏi của tài khoản đang được xử lý. Vui lòng chờ.";
            case "CONVERSATION_CHANGED" -> "Hội thoại đã thay đổi hoặc hết hạn. Hãy tải lại hội thoại trước khi gửi.";
            case "INVALID_INPUT" -> "Câu hỏi hoặc mã yêu cầu không hợp lệ. Câu hỏi tối đa 1200 ký tự.";
            case "ORDER_SELECTION_REQUIRED" -> "Chọn một đơn trong danh sách trên tài khoản để tiếp tục câu hỏi này; không cần nhập mã đơn.";
            case "ORDER_NOT_AVAILABLE" -> "Đơn vừa chọn không còn trong phạm vi tài khoản. Vui lòng tải lại danh sách và chọn đơn khác.";
            case "ORDER_SELECTION_CONFLICT" -> "Mã trong câu hỏi khác đơn đang chọn. Hãy chọn đúng đơn hoặc bỏ mã cũ trong câu hỏi.";
            case "ORDER_CHANGED" -> "Dữ liệu đơn vừa thay đổi trong lúc AI trả lời. Hãy thử lại để tra cứu thông tin mới nhất.";
            case "INVALID_ORDER_FILTER" -> "Tham số chọn đơn không hợp lệ: trang 0–1000, từ khóa tối đa 20 ký tự chữ, số hoặc gạch ngang; không nhận danh tính tài khoản.";
            default -> "Trợ lý AI tạm thời chưa phản hồi. Vui lòng thử lại.";
        });
        this.code=code; this.status=status; this.retryAfter=Math.max(0,retryAfter);
    }
    public String getCode() { return code; }
    public int getStatus() { return status; }
    public long getRetryAfter() { return retryAfter; }
}
