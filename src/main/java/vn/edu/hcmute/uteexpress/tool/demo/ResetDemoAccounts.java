package vn.edu.hcmute.uteexpress.tool.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.tool.database.AivenConnectionSettings;

/**
 * Công cụ chạy tay cho buổi demo, tuyệt đối không gọi khi ứng dụng khởi động.
 * Chỉ đổi mật khẩu; giữ nguyên role, trạng thái kích hoạt và trạng thái nội bộ.
 */
public final class ResetDemoAccounts {
    private static final String DEMO_PASSWORD = "123456";
    private static final Path BACKUP_DIRECTORY = Path.of("config/demo-account-backups");
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ResetDemoAccounts() { }

    public static void main(String[] args) {
        if (!applyRequested(args)) {
            System.err.println("Công cụ chỉ chạy tay: ResetDemoAccounts --apply. "
                    + "Sẽ đặt mật khẩu demo cho tất cả tài khoản hiện có.");
            System.exit(1);
            return;
        }
        try (Connection connection = AivenConnectionSettings.open()) {
            ResetResult result = apply(connection, BACKUP_DIRECTORY);
            System.out.printf("Đã cập nhật %d tài khoản; tạo %d hồ sơ nội bộ còn thiếu. "
                            + "Bản sao trước thay đổi: %s%n",
                    result.accountCount(), result.createdStaffCount(), result.backupPath());
        } catch (SQLException ex) {
            // Không in exception message vì driver có thể đưa URL/thông tin kết nối vào lỗi.
            System.err.println("Không hoàn tất công cụ. Kiểm tra kết nối và schema MySQL; "
                    + "không có URL hoặc thông tin kết nối được ghi ra màn hình.");
            System.exit(1);
        } catch (IOException ex) {
            System.err.println("Không đọc được cấu hình hoặc không ghi được bản sao trước thay đổi.");
            System.exit(1);
        } catch (RuntimeException ex) {
            System.err.println("Không hoàn tất công cụ. Kiểm tra cấu hình và tính nhất quán của tài khoản.");
            System.exit(1);
        }
    }

    static boolean applyRequested(String[] args) {
        return args != null && args.length == 1 && "--apply".equals(args[0]);
    }

    static ResetResult apply(Connection connection, Path backupDirectory) throws SQLException, IOException {
        if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName()) || !connection.getAutoCommit()) {
            throw new IllegalArgumentException("Cần kết nối MySQL mới, không có giao dịch đang mở.");
        }
        connection.setAutoCommit(false);
        boolean transactionEnded = false;
        try {
            // StaffAccountService cũng khóa staff theo id trước khi cập nhật app_user.
            // Khóa cùng thứ tự, rồi đọc toàn bộ user theo id trong cùng giao dịch.
            List<StaffSnapshot> staff = lockStaff(connection);
            List<UserSnapshot> users = lockUsers(connection);
            Map<Long, StaffSnapshot> staffByUser = validateScope(users, staff);
            Path backup = writeBackup(backupDirectory, users, staff);
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            LocalDateTime now = LocalDateTime.now(APPLICATION_ZONE);
            int createdStaff = 0;
            try (PreparedStatement passwordUpdate = connection.prepareStatement(
                         "UPDATE app_user SET password = ? WHERE id = ?");
                 PreparedStatement staffUpdate = connection.prepareStatement(
                         "UPDATE staff_account SET password_change_required = 0, "
                                 + "access_version = access_version + 1, revision = revision + 1, "
                                 + "updated_at = ? WHERE id = ? AND app_user_id = ?");
                 PreparedStatement staffInsert = connection.prepareStatement(
                         "INSERT INTO staff_account (app_user_id, active, password_change_required, "
                                 + "access_version, created_at, updated_at, issued_by, revision) "
                                 + "VALUES (?, 1, 0, 1, ?, ?, 'demo-account-reset', 0)")) {
                for (UserSnapshot user : users) {
                    // BCrypt sinh salt mới cho từng tài khoản, không chia sẻ cùng một hash.
                    passwordUpdate.setString(1, encoder.encode(DEMO_PASSWORD));
                    passwordUpdate.setLong(2, user.id());
                    requireSingleChange(passwordUpdate.executeUpdate());
                    if (!isInternalRole(user.role())) { continue; }
                    StaffSnapshot existing = staffByUser.get(user.id());
                    if (existing == null) {
                        staffInsert.setLong(1, user.id());
                        staffInsert.setObject(2, now);
                        staffInsert.setObject(3, now);
                        requireSingleChange(staffInsert.executeUpdate());
                        createdStaff++;
                    } else {
                        staffUpdate.setObject(1, now);
                        staffUpdate.setLong(2, existing.id());
                        staffUpdate.setLong(3, user.id());
                        requireSingleChange(staffUpdate.executeUpdate());
                    }
                }
            }
            connection.commit();
            transactionEnded = true;
            return new ResetResult(users.size(), createdStaff, backup);
        } catch (SQLException | IOException | RuntimeException ex) {
            try { connection.rollback(); transactionEnded = true; }
            catch (SQLException rollbackFailure) { ex.addSuppressed(rollbackFailure); }
            throw ex;
        } finally {
            // Nếu ROLLBACK thất bại, không bật autocommit vì có thể vô tình commit.
            if (transactionEnded) { connection.setAutoCommit(true); }
        }
    }

    private static List<UserSnapshot> lockUsers(Connection connection) throws SQLException {
        List<UserSnapshot> users = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, username, role, enabled, password FROM app_user ORDER BY id FOR UPDATE")) {
            while (rows.next()) {
                users.add(new UserSnapshot(rows.getLong("id"), rows.getString("username"),
                        rows.getString("role"), rows.getBoolean("enabled"), rows.getString("password")));
            }
        }
        return users;
    }

    private static List<StaffSnapshot> lockStaff(Connection connection) throws SQLException {
        List<StaffSnapshot> staff = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, app_user_id, active, password_change_required, access_version, "
                             + "created_at, updated_at, issued_by, revision FROM staff_account ORDER BY id FOR UPDATE")) {
            while (rows.next()) {
                staff.add(new StaffSnapshot(rows.getLong("id"), rows.getLong("app_user_id"),
                        rows.getBoolean("active"), rows.getBoolean("password_change_required"),
                        rows.getLong("access_version"), rows.getString("created_at"), rows.getString("updated_at"),
                        rows.getString("issued_by"), rows.getLong("revision")));
            }
        }
        return staff;
    }

    private static Map<Long, StaffSnapshot> validateScope(List<UserSnapshot> users, List<StaffSnapshot> staff) {
        Map<Long, UserSnapshot> usersById = new HashMap<>();
        for (UserSnapshot user : users) {
            AppUser.Role.valueOf(user.role());
            usersById.put(user.id(), user);
        }
        Map<Long, StaffSnapshot> staffByUser = new HashMap<>();
        for (StaffSnapshot account : staff) {
            UserSnapshot user = usersById.get(account.appUserId());
            if (user == null || !isInternalRole(user.role())
                    || staffByUser.put(account.appUserId(), account) != null) {
                throw new IllegalArgumentException("Hồ sơ nội bộ không khớp với role hiện có.");
            }
        }
        return staffByUser;
    }

    private static boolean isInternalRole(String role) {
        return "MANAGER".equals(role) || "ADMIN".equals(role);
    }

    private static void requireSingleChange(int count) throws SQLException {
        if (count != 1) { throw new SQLException("Số tài khoản cập nhật không khớp."); }
    }

    private static Path writeBackup(Path directory, List<UserSnapshot> users, List<StaffSnapshot> staff)
            throws IOException {
        Files.createDirectories(directory);
        String timestamp = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        Path backup = directory.resolve("accounts-before-reset-" + timestamp + "-" + UUID.randomUUID() + ".json");
        byte[] bytes = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(
                new BackupSnapshot(1, Instant.now().toString(), users, staff));
        // CREATE_NEW bảo vệ bản sao cũ; force hoàn tất ghi xuống đĩa trước mọi UPDATE.
        try (FileChannel channel = FileChannel.open(backup, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) { channel.write(buffer); }
            channel.force(true);
        }
        return backup;
    }

    public record UserSnapshot(long id, String username, String role, boolean enabled, String password) { }
    public record StaffSnapshot(long id, long appUserId, boolean active, boolean passwordChangeRequired,
            long accessVersion, String createdAt, String updatedAt, String issuedBy, long revision) { }
    public record BackupSnapshot(int schemaVersion, String createdAt, List<UserSnapshot> users,
            List<StaffSnapshot> staff) { }
    record ResetResult(int accountCount, int createdStaffCount, Path backupPath) { }
}
