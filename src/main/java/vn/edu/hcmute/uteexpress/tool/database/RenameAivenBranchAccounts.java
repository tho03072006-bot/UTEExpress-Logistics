package vn.edu.hcmute.uteexpress.tool.database;

import java.io.PrintStream;
import java.sql.*;
import java.util.*;

/**
 * Chạy tay: đổi tên đăng nhập demo của Manager/Shipper theo mẫu dễ nhớ {@code vaitro_chinhanh[so]}
 * (manager_sgn, shipper_sgn1, shipper_han1...). Chỉ đổi cột username của đúng các tài khoản trong bảng ánh xạ,
 * giữ nguyên id, mật khẩu, chi nhánh, đơn hàng; không sửa nhật ký cũ (nhật ký vẫn ghi tên tại thời điểm thao tác).
 * Tăng access_version để phiên đăng nhập đang mở theo tên cũ bị thu hồi. Chạy lại lần hai thì bỏ qua tài khoản đã đổi.
 */
public final class RenameAivenBranchAccounts {
    private static final String LOCK_NAME = "uteexpress-schema-initialize";
    /** Tên cũ, tên mới, vai trò và chi nhánh bắt buộc phải khớp trước khi đổi. */
    record Rename(String from, String to, String role, String branch) { }
    static final List<Rename> RENAMES = List.of(
            new Rename("manager1", "manager_sgn", "MANAGER", "SGN"),
            new Rename("shipper1", "shipper_sgn1", "SHIPPER", "SGN"),
            new Rename("shipper2", "shipper_sgn2", "SHIPPER", "SGN"),
            new Rename("shipper3", "shipper_han1", "SHIPPER", "HAN"));
    enum State { PENDING, DONE }
    private RenameAivenBranchAccounts() { }

    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("--print-plan", "--check", "--apply").contains(args[0])) {
            System.err.println("Dùng --print-plan (không kết nối), --check (chỉ đọc) hoặc --apply (người vận hành tự chạy).");
            System.exit(1); return;
        }
        try {
            if (args[0].equals("--print-plan")) { printPlan(System.out); return; }
            try (var connection = AivenConnectionSettings.open()) {
                if (args[0].equals("--apply")) {
                    int renamed = apply(connection);
                    System.out.println("Đã đổi tên " + renamed + " tài khoản. Không đổi mật khẩu, chi nhánh hay dữ liệu đơn.");
                }
                int pending = check(connection, System.out);
                if (pending > 0) System.exit(2);
            }
        } catch (Exception ex) {
            System.err.println("Chưa hoàn tất kiểm tra/đổi tên. Không in thông tin kết nối.");
            System.err.println(errorSummary(ex));
            System.exit(1);
        }
    }

    public static void printPlan(PrintStream output) {
        output.println("-- Đổi tên đăng nhập demo theo chi nhánh; chỉ cột username, giữ nguyên id/mật khẩu/chi nhánh.");
        for (var rename : RENAMES) output.println("-- " + rename.from() + " -> " + rename.to() + " (" + rename.role() + ", " + rename.branch() + ")");
    }

    /** Không in message của JDBC: có thể chứa URL, tên tài khoản hoặc thông tin cấu hình riêng. */
    static String errorSummary(Exception ex) {
        return "Exception=" + ex.getClass().getName() + (ex instanceof SQLException sql
                ? "; SQLState=" + (sql.getSQLState()!=null && sql.getSQLState().matches("[A-Z0-9]{5}") ? sql.getSQLState() : "unknown")
                    + "; errorCode=" + sql.getErrorCode() : "");
    }

    /** Chỉ đọc. Trả về số tài khoản còn chờ đổi; ném lỗi nếu dữ liệu không khớp vai trò/chi nhánh hoặc tên mới đã bị chiếm. */
    public static int check(Connection connection, PrintStream output) throws SQLException {
        connection.setReadOnly(true);
        validateTarget(connection);
        int pending = 0;
        output.println("Kiểm tra chỉ đọc: KHÔNG đổi tên, KHÔNG ghi dữ liệu.");
        for (var rename : RENAMES) {
            var state = state(connection, rename);
            if (state == State.PENDING) pending++;
            output.println(rename.from() + " -> " + rename.to() + ": " + (state == State.DONE ? "đã đổi" : "chờ đổi"));
        }
        output.println(pending == 0 ? "Tên đăng nhập đã theo chi nhánh." : "Còn " + pending + " tài khoản chờ đổi. Người vận hành review rồi tự chạy --apply.");
        return pending;
    }

    public static int apply(Connection connection) throws SQLException {
        if (!connection.getAutoCommit() || connection.isReadOnly()) throw new IllegalArgumentException("Đổi tên cần kết nối ghi riêng.");
        validateTarget(connection);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT GET_LOCK('" + LOCK_NAME + "', 10)")) {
            if (!rows.next() || rows.getInt(1) != 1) throw new IllegalStateException("Đang có tiến trình nâng cấp khác; chưa ghi dữ liệu.");
        }
        int renamed = 0;
        boolean transactionEnded = false;
        try {
            connection.setAutoCommit(false);
            // Kiểm tra hết trước khi ghi: một tài khoản không khớp thì không đổi tài khoản nào.
            var pending = new ArrayList<Rename>();
            for (var rename : RENAMES) if (state(connection, rename, true) == State.PENDING) pending.add(rename);
            for (var rename : pending) {
                try (var update = connection.prepareStatement("UPDATE app_user SET username = ?, access_version = access_version + 1"
                        + " WHERE username = ? AND role = ?")) {
                    update.setQueryTimeout(30);
                    update.setString(1, rename.to()); update.setString(2, rename.from()); update.setString(3, rename.role());
                    if (update.executeUpdate() != 1) throw new IllegalStateException("Không đổi được " + rename.from() + "; dừng lại.");
                    renamed++;
                }
            }
            connection.commit();
            transactionEnded = true;
        } catch (SQLException | RuntimeException ex) {
            try { connection.rollback(); transactionEnded = true; }
            catch (SQLException rollbackFailure) { ex.addSuppressed(rollbackFailure); }
            throw ex;
        } finally {
            // Không bật auto-commit nếu rollback thất bại: JDBC có thể vô tình commit phần đang dở.
            try { if (transactionEnded) connection.setAutoCommit(true); }
            finally {
                try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('" + LOCK_NAME + "')"); }
            }
        }
        return renamed;
    }

    /** DONE: tên mới đã có đúng vai trò/chi nhánh và tên cũ không còn; PENDING: tên cũ đúng vai trò/chi nhánh, tên mới còn trống. */
    static State state(Connection connection, Rename rename) throws SQLException {
        return state(connection, rename, false);
    }
    private static State state(Connection connection, Rename rename, boolean lock) throws SQLException {
        var oldAccount = account(connection, rename.from(), lock);
        var newAccount = account(connection, rename.to(), lock);
        if (oldAccount == null && newAccount != null) { requireMatch(rename, newAccount); return State.DONE; }
        if (oldAccount != null && newAccount == null) { requireMatch(rename, oldAccount); return State.PENDING; }
        if (oldAccount == null) throw new IllegalStateException("Không có tài khoản " + rename.from() + " hay " + rename.to() + ". Không tự tạo.");
        throw new IllegalStateException("Tên mới " + rename.to() + " đã thuộc một tài khoản khác. Không ghi đè.");
    }

    record Account(String role, String branch) { }

    private static void requireMatch(Rename rename, Account account) {
        if (!rename.role().equals(account.role()) || !rename.branch().equals(account.branch()))
            throw new IllegalStateException("Tài khoản " + rename.from() + "/" + rename.to() + " là " + account.role() + " chi nhánh "
                    + account.branch() + ", không khớp " + rename.role() + " chi nhánh " + rename.branch() + ". Không đổi tên.");
    }

    private static Account account(Connection connection, String username, boolean lock) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT u.role, b.code FROM app_user u LEFT JOIN staff_branch s ON s.user_id = u.id"
                + " LEFT JOIN branch b ON b.id = s.branch_id WHERE u.username = ?" + (lock ? " FOR UPDATE" : ""))) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) { return rows.next() ? new Account(rows.getString(1), rows.getString(2)) : null; }
        }
    }

    private static void validateTarget(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        if (!"MySQL".equals(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() < 8)
            throw new IllegalArgumentException("Chỉ chạy trên MySQL 8+ đã có schema UTEExpress.");
        for (String table : List.of("app_user", "staff_branch", "branch"))
            if (!UpgradeAivenOrderSubmissions.tableExists(connection, table))
                throw new IllegalStateException("Thiếu bảng " + table + "; không tự khởi tạo database.");
    }
}
