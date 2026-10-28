package vn.edu.hcmute.uteexpress.tool.admin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import jakarta.validation.Validation;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.hcmute.uteexpress.dto.admin.StaffPasswordRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.tool.database.AivenConnectionSettings;

/** Công cụ quản trị DB chạy tay, không phải endpoint web hay seed. Không tạo/nâng quyền/mở khóa tài khoản. */
public final class RecoverIssuedStaffAccounts {
    static final Path PRIVATE_FILE = Path.of("config/recovery/issued-staff-credentials.properties");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ", LOWER = "abcdefghjkmnpqrstuvwxyz";
    // Không dùng ký tự Properties.store sẽ escape, để người dùng copy trực tiếp từ file vào form.
    private static final String DIGITS = "23456789", SYMBOLS = "@%*-_+";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private RecoverIssuedStaffAccounts() { }

    record Credential(String username, AppUser.Role role, String password, String hash) {
        @Override public String toString() { return username + " (" + role + ", credentials redacted)"; }
    }
    private record Account(long userId, long staffId, long userRevision, long staffRevision) { }
    @FunctionalInterface interface CredentialWriter { void write() throws IOException; }

    public static void main(String[] args) {
        try {
            if (args.length == 1 && "--verify".equals(args[0])) { verifyFile(); return; }
            if (args.length != 3 || !"--apply".equals(args[0]))
                throw new IllegalArgumentException("Dùng --apply <username-admin> <username-manager>, hoặc --verify (chỉ đọc).");
            validateNames(args[1], args[2]);
            if (Files.exists(PRIVATE_FILE, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("File mật khẩu tạm đã tồn tại. Không ghi đè hoặc tự cấp lại lần nữa; dùng --verify.");
            var credentials = List.of(generate(args[1], AppUser.Role.ADMIN), generate(args[2], AppUser.Role.MANAGER));
            preparePrivateFile();
            try (var connection = AivenConnectionSettings.open()) {
                recover(connection, credentials, () -> writeCredentials(credentials));
            }
            System.out.println("ĐÃ KHÔI PHỤC HAI TÀI KHOẢN NỘI BỘ; giữ nguyên ID, quyền và dữ liệu nghiệp vụ.");
            System.out.println("Thu hồi phiên cũ; bắt buộc đổi mật khẩu khi đăng nhập. Không gửi OTP.");
            System.out.println("Mật khẩu tạm không in ra console; xem file private: " + PRIVATE_FILE.toAbsolutePath().normalize());
            verifyFile();
        } catch (Exception ex) {
            System.err.println("KHÔNG XÁC NHẬN ĐƯỢC KHÔI PHỤC. Không tự chạy lại hoặc xóa file mật khẩu tạm.");
            if (ex instanceof IllegalArgumentException) System.err.println(ex.getMessage());
            else System.err.println("Loại lỗi: " + ex.getClass().getSimpleName()
                    + (ex instanceof SQLException sql ? "; SQLState=" + sql.getSQLState() : ""));
            System.err.println("Nếu đã có file, giữ riêng và dùng --verify để đối chiếu trạng thái database.");
            System.exit(1);
        }
    }

    static Credential generate(String username, AppUser.Role role) {
        char[] value = new char[24];
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        value[0] = pick(UPPER); value[1] = pick(LOWER); value[2] = pick(DIGITS); value[3] = pick(SYMBOLS);
        for (int i = 4; i < value.length; i++) value[i] = pick(all);
        for (int i = value.length - 1; i > 0; i--) { int j = RANDOM.nextInt(i + 1); char temp = value[i]; value[i] = value[j]; value[j] = temp; }
        String password = new String(value); Arrays.fill(value, '\0');
        return new Credential(username, role, password, ENCODER.encode(password));
    }
    private static char pick(String characters) { return characters.charAt(RANDOM.nextInt(characters.length())); }

    static void recover(Connection connection, List<Credential> credentials, CredentialWriter writer) throws SQLException, IOException {
        validateCredentials(credentials);
        if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName()) || connection.getMetaData().getDatabaseMajorVersion() < 8
                || !connection.getAutoCommit()) throw new IllegalArgumentException("Cần kết nối Aiven MySQL 8 riêng, chưa có giao dịch khác.");
        connection.setAutoCommit(false);
        try {
            // Cùng thứ tự khóa với StaffAccountRepository.lockAccounts của ứng dụng.
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT id FROM staff_account ORDER BY id FOR UPDATE")) {
                while (result.next()) { /* giữ khóa đến commit/rollback */ }
            }
            var accounts = new ArrayList<Account>();
            for (var credential : credentials) accounts.add(lockedAccount(connection, credential));
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            for (int i = 0; i < credentials.size(); i++) update(connection, accounts.get(i), credentials.get(i), now);
            // Lưu và fsync file private trước commit để không mất mật khẩu sau khi DB đã đổi.
            writer.write();
            connection.commit();
        } catch (SQLException | IOException | RuntimeException ex) {
            try { connection.rollback(); } catch (SQLException rollbackError) { ex.addSuppressed(rollbackError); }
            throw ex;
        }
    }

    private static Account lockedAccount(Connection connection, Credential credential) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT u.id AS user_id, u.username, u.role, u.enabled, u.locked_by_admin, "
                + "u.password, u.revision AS user_revision, s.id AS staff_id, s.active, s.revision AS staff_revision "
                + "FROM app_user u JOIN staff_account s ON s.app_user_id = u.id WHERE u.username = ? FOR UPDATE")) {
            statement.setString(1, credential.username());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Không tìm thấy tài khoản nội bộ được cấp: " + credential.username());
                if (!credential.username().equals(result.getString("username")) || !credential.role().name().equals(result.getString("role"))
                        || !result.getBoolean("enabled") || result.getBoolean("locked_by_admin") || !result.getBoolean("active"))
                    throw new IllegalArgumentException("Sai vai trò hoặc tài khoản bị khóa/chưa hoạt động: " + credential.username());
                if (ENCODER.matches(credential.password(), result.getString("password")))
                    throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ.");
                return new Account(result.getLong("user_id"), result.getLong("staff_id"), result.getLong("user_revision"), result.getLong("staff_revision"));
            }
        }
    }
    private static void update(Connection connection, Account account, Credential credential, LocalDateTime now) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE app_user SET password = ?, otp_code = NULL, otp_expiry = NULL, "
                + "access_version = access_version + 1, revision = revision + 1 WHERE id = ? AND revision = ?")) {
            statement.setString(1, credential.hash()); statement.setLong(2, account.userId()); statement.setLong(3, account.userRevision());
            exactlyOne(statement.executeUpdate());
        }
        try (var statement = connection.prepareStatement("UPDATE staff_account SET password_change_required = 1, access_version = access_version + 1, "
                + "revision = revision + 1, updated_at = ? WHERE id = ? AND revision = ?")) {
            statement.setObject(1, now); statement.setLong(2, account.staffId()); statement.setLong(3, account.staffRevision());
            exactlyOne(statement.executeUpdate());
        }
        try (var statement = connection.prepareStatement("DELETE FROM email_change_request WHERE user_id = ?")) {
            statement.setLong(1, account.userId()); statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("INSERT INTO audit_event (actor, action, target, detail, created_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, "database-operator"); statement.setString(2, "STAFF_EMERGENCY_RECOVERY");
            statement.setString(3, "user:" + account.userId());
            statement.setString(4, "Khoi phuc tai khoan noi bo theo yeu cau chu tai khoan quen mat khau; thu hoi phien cu; bat buoc doi mat khau. role=" + credential.role());
            statement.setObject(5, now); exactlyOne(statement.executeUpdate());
        }
    }
    private static void exactlyOne(int rows) throws SQLException { if (rows != 1) throw new SQLException("Dữ liệu đã thay đổi; hủy toàn bộ giao dịch.", "40001"); }
    private static void validateNames(String admin, String manager) {
        if (admin == null || manager == null || !admin.matches("[a-z][a-z0-9._-]{3,49}") || !manager.matches("[a-z][a-z0-9._-]{3,49}") || admin.equals(manager))
            throw new IllegalArgumentException("Cần hai username nội bộ khác nhau, đúng định dạng.");
    }
    private static void validateCredentials(List<Credential> credentials) {
        if (credentials == null || credentials.size() != 2 || credentials.get(0) == null || credentials.get(1) == null)
            throw new IllegalArgumentException("Chỉ khôi phục cặp Admin/Manager chỉ định.");
        validateNames(credentials.get(0).username(), credentials.get(1).username());
        if (credentials.get(0).role() != AppUser.Role.ADMIN || credentials.get(1).role() != AppUser.Role.MANAGER)
            throw new IllegalArgumentException("Không đổi vai trò hoặc khôi phục tài khoản công khai bằng công cụ này.");
        if (Objects.equals(credentials.get(0).password(), credentials.get(1).password()))
            throw new IllegalArgumentException("Hai tài khoản phải có mật khẩu khác nhau.");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (var credential : credentials) {
                var form = new StaffPasswordRequest(); form.setPassword(credential.password()); form.setConfirmPassword(credential.password());
                if (!factory.getValidator().validate(form).isEmpty() || credential.hash() == null || !ENCODER.matches(credential.password(), credential.hash()))
                    throw new IllegalArgumentException("Mật khẩu/bản băm không đáp ứng validation nội bộ.");
                form.clearSecrets();
            }
        }
    }

    static void preparePrivateFile() throws IOException {
        Path folder = PRIVATE_FILE.toAbsolutePath().getParent(); Files.createDirectories(folder);
        if (!folder.toRealPath().startsWith(Path.of(".").toRealPath())) throw new IOException("Private folder nằm ngoài workspace.");
        restrict(folder, true);
        Files.createFile(PRIVATE_FILE); // CREATE_NEW: tuyệt đối không ghi đè file cũ.
        restrict(PRIVATE_FILE, false);
    }
    private static void restrict(Path path, boolean directory) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) { posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------")); return; }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) throw new IOException("Không xác nhận được quyền riêng tư của file.");
        var principal = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        var entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(principal).setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        acl.setAcl(List.of(entry.build()));
        if (acl.getAcl().stream().anyMatch(e -> e.type() == AclEntryType.ALLOW && !e.principal().equals(principal)))
            throw new IOException("File còn quyền đọc ngoài người dùng hiện tại; chưa đổi mật khẩu database.");
    }
    private static void writeCredentials(List<Credential> credentials) throws IOException {
        var values = new Properties(); values.setProperty("login-url", "http://localhost:8080/noi-bo/dang-nhap");
        values.setProperty("created-at", LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString());
        values.setProperty("note", "PRIVATE - require successful CLI confirmation; change passwords on first login; delete this file after use.");
        for (var credential : credentials) {
            String prefix = credential.role().name().toLowerCase(Locale.ROOT);
            values.setProperty(prefix + ".username", credential.username()); values.setProperty(prefix + ".temporary-password", credential.password());
        }
        try (var output = new FileOutputStream(PRIVATE_FILE.toFile()); var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            values.store(writer, "LOCAL ONLY - NEVER COMMIT OR SHARE"); writer.flush(); output.getFD().sync();
        } finally { values.clear(); }
    }
    private static void verifyFile() throws IOException, SQLException {
        var values = new Properties();
        try (var reader = Files.newBufferedReader(PRIVATE_FILE, StandardCharsets.UTF_8)) { values.load(reader); }
        try (var connection = AivenConnectionSettings.open()) {
            connection.setReadOnly(true);
            validateNames(values.getProperty("admin.username"), values.getProperty("manager.username"));
            for (String role : List.of("ADMIN", "MANAGER")) {
                String prefix = role.toLowerCase(Locale.ROOT);
                try (var statement = connection.prepareStatement("SELECT u.password, u.role, u.enabled, u.locked_by_admin, s.active, s.password_change_required "
                        + "FROM app_user u JOIN staff_account s ON s.app_user_id = u.id WHERE u.username = ?")) {
                    statement.setString(1, values.getProperty(prefix + ".username"));
                    try (var result = statement.executeQuery()) {
                        if (!result.next() || !role.equals(result.getString("role")) || !result.getBoolean("enabled") || result.getBoolean("locked_by_admin")
                                || !result.getBoolean("active") || !result.getBoolean("password_change_required")
                                || !ENCODER.matches(values.getProperty(prefix + ".temporary-password"), result.getString("password")))
                            throw new IllegalArgumentException("Thông tin cấp lại không còn khớp/trạng thái đã đổi; không tự reset lại: " + role);
                    }
                }
                System.out.println("VERIFY " + role + ": mật khẩu tạm khớp BCrypt; tài khoản được cấp, hoạt động, bắt buộc đổi mật khẩu.");
            }
        } finally { values.clear(); }
    }
}
