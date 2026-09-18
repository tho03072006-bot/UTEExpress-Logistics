package vn.edu.hcmute.uteexpress.tool.admin;

import java.io.Console;
import java.sql.*;
import java.util.Arrays;
import jakarta.validation.Validation;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.hcmute.uteexpress.dto.admin.StaffAccountCreateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.tool.database.AivenConnectionSettings;

/**
 * Công cụ chạy tay bởi người quản trị DB, không chạy cùng ứng dụng hoặc seed dữ liệu.
 * Chỉ tạo Admin đầu tiên; không nâng quyền tài khoản khách hàng/shipper đã có.
 */
public final class ProvisionInitialAdmin {
    private ProvisionInitialAdmin() { }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Hãy chạy trong terminal tương tác để nhập mật khẩu ẩn.");
            System.exit(1);
        }
        try {
            StaffAccountCreateRequest form = new StaffAccountCreateRequest();
            form.setUsername(console.readLine("Tên đăng nhập Admin mới: "));
            form.setFullName(console.readLine("Họ tên: "));
            form.setEmail(console.readLine("Email công việc: "));
            form.setRole(AppUser.Role.ADMIN);
            char[] password = console.readPassword("Mật khẩu tạm (12–64 ký tự, hoa/thường/số/ký tự đặc biệt): ");
            char[] confirmation = console.readPassword("Nhập lại mật khẩu: ");
            if (password == null || confirmation == null) {
                throw new IllegalArgumentException("Đã hủy thao tác.");
            }
            form.setPassword(new String(password));
            form.setConfirmPassword(new String(confirmation));
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                var errors = factory.getValidator().validate(form);
                if (!errors.isEmpty()) {
                    throw new IllegalArgumentException(errors.iterator().next().getMessage());
                }
            }
            String hash = new BCryptPasswordEncoder().encode(form.getPassword());
            form.clearSecrets();
            try (Connection connection = AivenConnectionSettings.open()) {
                createInitialAdmin(connection, form, hash);
            }
            console.printf("Đã cấp Admin đầu tiên. Đăng nhập /noi-bo/dang-nhap và đổi mật khẩu trước khi sử dụng.%n");
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        } catch (SQLException ex) {
            System.err.println("Không thể cấp Admin; giao dịch đã được hủy. Kiểm tra kết nối, bảng staff_account và tài khoản/email trùng. SQLState: " + ex.getSQLState());
            System.exit(1);
        } catch (java.io.IOException ex) {
            System.err.println("Không đọc được config/aiven-local.properties.");
            System.exit(1);
        }
    }

    static void createInitialAdmin(Connection connection, StaffAccountCreateRequest form, String hash)
            throws SQLException {
        if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName())) {
            throw new IllegalArgumentException("Công cụ chỉ hỗ trợ MySQL trên Aiven.");
        }
        // Khóa có tên bảo vệ trường hợp bảng staff còn trống, không dựa vào khóa dòng rỗng.
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT GET_LOCK('uteexpress-initial-admin', 10)")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new IllegalArgumentException("Đang có tiến trình cấp Admin khác. Hãy thử lại sau.");
            }
        }
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM staff_account")) {
                result.next();
                if (result.getLong(1) != 0) {
                    throw new IllegalArgumentException("Đã có tài khoản nội bộ. Hãy dùng chức năng cấp tài khoản của Admin hiện có.");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM app_user WHERE LOWER(username) = ? OR LOWER(email) = ?")) {
                statement.setString(1, form.getUsername());
                statement.setString(2, form.getEmail());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getLong(1) != 0) {
                        throw new IllegalArgumentException("Tên đăng nhập hoặc email đã tồn tại. Không nâng quyền tài khoản đã đăng ký OTP.");
                    }
                }
            }
            long userId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO app_user (username, password, full_name, email, role, enabled, created_at) "
                            + "VALUES (?, ?, ?, ?, 'ADMIN', 1, ?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, form.getUsername());
                statement.setString(2, hash);
                statement.setString(3, form.getFullName());
                statement.setString(4, form.getEmail());
                statement.setObject(5, java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
                statement.executeUpdate();
                try (ResultSet result = statement.getGeneratedKeys()) {
                    if (!result.next()) { throw new SQLException("Không nhận được ID tài khoản."); }
                    userId = result.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO staff_account (app_user_id, active, password_change_required, access_version, "
                            + "created_at, updated_at, issued_by, revision) "
                            + "VALUES (?, 1, 1, 0, ?, ?, 'database-operator', 0)")) {
                statement.setLong(1, userId);
                var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
                statement.setObject(2, now);
                statement.setObject(3, now);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT RELEASE_LOCK('uteexpress-initial-admin')");
            }
        }
    }
}
