package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class AuditFilterRequest {
    public enum Category {
        ALL("Tất cả thao tác"), ACCOUNT("Quản lý tài khoản"), OPERATIONS("Vận hành đơn hàng"),
        PRICING("Cước và chính sách"), PROMOTION("Khuyến mãi"), SECURITY("Bảo mật truy cập");
        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
    @Size(max = 50, message = "Tên người thực hiện tối đa 50 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9._:@-]*", message = "Nhập tên đăng nhập hợp lệ, không chứa khoảng trắng hoặc ký tự đặc biệt khác.")
    private String operator = "";
    @NotNull(message = "Chọn nhóm thao tác hợp lệ.")
    private Category category = Category.ALL;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    public String getOperator() { return operator; }
    public void setOperator(String value) { operator = value == null ? "" : value.strip(); }
    public Category getCategory() { return category; }
    public void setCategory(Category value) { category = value; }
    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate value) { from = value; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate value) { to = value; }
    public boolean isEmpty() { return operator.isEmpty() && category == Category.ALL && from == null && to == null; }
}
