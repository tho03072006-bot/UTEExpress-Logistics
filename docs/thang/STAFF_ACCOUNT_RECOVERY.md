# Khôi phục tài khoản nội bộ được cấp

## Lần thực hiện 16/10/2026

Theo yêu cầu trực tiếp của chủ tài khoản, đã cấp lại mật khẩu cho đúng hai tài khoản đang có trên Aiven: `admin` (ADMIN) và `manager1` (MANAGER). Không tạo tài khoản mới, không đổi ID/quyền/email, không sửa dữ liệu đơn hàng và không gửi OTP.

Mật khẩu tạm khác nhau, được sinh bằng SecureRandom, 24 ký tự, đáp ứng validation hiện tại. Database chỉ nhận hash BCrypt. Công cụ thu hồi phiên cũ, tăng revision, xóa OTP/yêu cầu đổi email đang chờ của đúng hai tài khoản, ghi nhật ký `STAFF_EMERGENCY_RECOVERY`, yêu cầu đổi mật khẩu ở lần đăng nhập tiếp theo. Nếu một phần lỗi thì rollback cả hai tài khoản.

**Không có mật khẩu thật trong tài liệu hoặc mã nguồn.** Chỉ xem trên máy đã thực hiện:

```text
config/recovery/issued-staff-credentials.properties
```

- `admin.username` và `admin.temporary-password`: tài khoản Admin và mật khẩu tạm.
- `manager.username` và `manager.temporary-password`: tài khoản Manager và mật khẩu tạm.
- Copy phần giá trị sau dấu `=`, không thêm khoảng trắng hoặc dấu ngoặc kép.
- Đăng nhập `/noi-bo/dang-nhap`, đổi mật khẩu khi hệ thống yêu cầu, sau đó đăng nhập lại bằng mật khẩu mới.
- Sau khi cả hai đã đổi mật khẩu thành công và lưu mật khẩu mới an toàn, xóa file private này trên máy. Không chia sẻ file/ảnh chụp hoặc đưa vào Git.

Folder `config/recovery/` bị gitignore, nằm ngoài resources và không đóng gói trong JAR. ACL của folder/file chỉ cấp quyền cho người dùng Windows đang thực hiện. Không đưa giá trị trong file này sang `.example`, issue, PR hoặc chat.

## Công cụ quản trị

`RecoverIssuedStaffAccounts` là CLI chạy tay với quyền truy cập database Aiven của người quản trị. Không có endpoint recovery công khai, không chạy khi khởi động Spring Boot. Công cụ không mở khóa, không kích hoạt tài khoản, không nâng quyền USER/SHIPPER và từ chối username không có hồ sơ staff/vai trò đúng. `ProvisionInitialAdmin` vẫn chỉ dành cho cấp Admin đầu tiên, không dùng thay cho recovery.

Không chạy lại lệnh thay đổi mật khẩu của lần này. File private tồn tại thì `--apply` tự dừng, không ghi đè hoặc cấp lại ngầm. Khi cần đối chiếu, **chỉ đọc** bằng Java 17 tại root project, sau khi có `target/runtime-classpath.txt`:

```powershell
$taskRecoveryClasspath = 'target/classes;' + (Get-Content target/runtime-classpath.txt -Raw).Trim()
& 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe' -cp $taskRecoveryClasspath vn.edu.hcmute.uteexpress.tool.admin.RecoverIssuedStaffAccounts --verify
```

Sau khi người dùng đổi mật khẩu tạm, `--verify` báo không khớp là đúng; **không** vì thế mà cấp lại hoặc quay về mật khẩu cũ. Nếu database mất mạng đúng lúc commit, giữ file private và dùng `--verify` để xác nhận, không tự reset lần hai.

Thông thường Admin còn truy cập được phải dùng giao diện **Quản lý tài khoản → Cấp lại mật khẩu**. CLI chỉ là phương án ngoài ứng dụng cho sự cố mất quyền truy cập, theo yêu cầu/xác minh của chủ tài khoản và quản trị DB.

## Kiểm tra đã thực hiện

- 10 unit test riêng: mật khẩu mạnh/khác nhau, không lộ qua toString, đúng tài khoản/vai trò, không tự mở khóa, không tạo/nâng quyền, rollback khi thiếu Manager/cập nhật lỗi/ghi file lỗi/audit lỗi, từ chối mật khẩu yếu và transaction của tiến trình khác.
- Đối chiếu BCrypt sau khi commit: cả hai khớp mật khẩu tạm và có cờ bắt buộc đổi mật khẩu.
- Đăng nhập HTTP thật trên app đang chạy: cả hai vào đúng dashboard theo vai trò, sau đó bị chuyển đến trang đổi mật khẩu đúng yêu cầu. Các phiên kiểm tra đã được đăng xuất.
- Kiểm tra Git ignore và ACL NTFS: file mật khẩu riêng không được theo dõi, chỉ người dùng Windows hiện tại có quyền truy cập.
- Chạy lại `mvn -q verify` thành công sau khôi phục: **429 test, 0 failure, 0 error, 0 skipped**. Bao gồm CRUD cả bốn vai trò bằng giao dịch rollback trên Aiven, Manager bị chặn GET/POST quản lý tài khoản và bộ test recovery mới.

Commit riêng tính năng này theo nhóm 13 trong COMMIT_PLAN_2026_10_16.md. Không commit file mật khẩu tạm.
