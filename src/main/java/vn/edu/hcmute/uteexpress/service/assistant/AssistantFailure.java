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
            default -> "Trợ lý AI tạm thời chưa phản hồi. Vui lòng thử lại.";
        });
        this.code=code; this.status=status; this.retryAfter=Math.max(0,retryAfter);
    }
    public String getCode() { return code; }
    public int getStatus() { return status; }
    public long getRetryAfter() { return retryAfter; }
}
