# Admin CRUD cả bốn vai trò — 10/10/2026

## Cách dùng

Restart ứng dụng bằng JDK 17 sau khi cập nhật code, đăng nhập Admin **được cấp** tại `/noi-bo/dang-nhap`, đổi mật khẩu tạm nếu được yêu cầu. Chọn **Quản lý tài khoản · 4 vai trò** hoặc mở `/admin/quan-ly-tai-khoan`.

Chỉ Admin có quyền quản lý tài khoản Admin, Manager, User và Shipper. Manager/User/Shipper bị chặn cả GET và POST; Service cũng kiểm tra lại Admin đang hoạt động từ Aiven. Người dùng vẫn được tự đăng ký USER/đổi thông tin của mình theo luồng công khai sẵn có; đó không phải quyền CRUD quản trị tài khoản khác.

| Nhóm thay đổi | Chức năng | Validation / bảo vệ |
| --- | --- | --- |
| Cấp mới | Tạo cả 4 vai trò; lọc vai trò, phân trang 20 tài khoản | Username/email không trùng kể cả tài khoản chưa xác thực; kiểm tra tên, điện thoại, email, role, mật khẩu |
| Sửa | Họ tên, email, điện thoại, quyền và trạng thái | Username bất biến; mật khẩu xác nhận của Admin + lý do; revision chống form cũ; không tự sửa quyền/trạng thái |
| Cấp lại mật khẩu | Cả 4 vai trò | Admin xác nhận mật khẩu + lý do; mật khẩu mới khác cũ; không reset tài khoản bị khóa/chưa kích hoạt |
| Xóa | Xóa thật tài khoản chưa có dữ liệu liên kết | Gõ đúng username + mật khẩu Admin + lý do + revision; không xóa chính mình/Admin cuối cùng; không cascade |
| Quyền và phiên | Thu hồi phiên của cả User/Shipper và nhân sự nội bộ | Khóa/xóa/đổi quyền/mật khẩu có hiệu lực ở request tiếp theo; mở khóa không hồi sinh phiên đã thu hồi; Aiven lỗi thì từ chối 503 |
| OTP | Giữ đăng ký/khôi phục công khai cho User/Shipper | OTP không mở lại tài khoản Admin đã khóa; Admin/Manager không đăng ký/kích hoạt/khôi phục qua OTP |
| Nhật ký | Tạo/sửa/xóa/reset lưu cùng transaction | Giữ nhật ký sau xóa; không lưu trường mật khẩu/OTP/hash vào chi tiết |

Admin/Manager luôn được **cấp tài khoản mới riêng**, có cả `app_user` và `staff_account`, không tạo bằng OTP và phải đổi mật khẩu ở lần đăng nhập đầu/cấp lại. Không biến tài khoản OTP thành Admin/Manager, không hạ tài khoản nội bộ sang nhóm công khai. Admin có thể đổi Admin ↔ Manager cho người khác; đổi User ↔ Shipper chỉ khi chưa có dữ liệu liên kết. Các hạn chế này giữ đúng loại tài khoản và lịch sử nghiệp vụ, không phải cấp quyền CRUD cho Manager.

Khóa Shipper còn đơn PENDING_PICKUP/PICKED_UP/IN_TRANSIT bị từ chối; phải xử lý/điều phối các đơn đó trước. Khi đổi thông tin/cấp lại mật khẩu tại trang mới, OTP và yêu cầu đổi email đang chờ bị hủy để không ghi đè email/mật khẩu Admin vừa cập nhật. Việc mở tài khoản chưa xác thực là xác nhận cấp trực tiếp của Admin, không phải kết quả OTP.

Xóa bị chặn nếu có đơn gửi/đơn được phân công, địa chỉ, giỏ đơn, đánh giá, bằng chứng giao, yêu cầu đổi email hoặc còn hồ sơ nhân sự do tài khoản đó đã cấp. Nếu có dữ liệu, dùng khóa, không xóa đơn/payment/ảnh của Thọ/Tài. Ràng buộc FK bảo vệ cả trường hợp dữ liệu liên kết phát sinh đồng thời; lỗi sẽ rollback cả tài khoản và nhật ký thao tác xóa.

## Chi tiết nhập liệu

- Username 4–50 ký tự, bắt đầu bằng chữ ASCII, chỉ chữ/số/`.`/`_`/`-`; strip và viết thường. Sau khi cấp không sửa username để giữ truy vết.
- Họ tên 2–100 ký tự, có chữ; chấp nhận tiếng Việt/dấu tên, từ chối số, HTML, ký tự điều khiển.
- Email tối đa 100 ký tự, đúng cú pháp/tên miền; strip, viết thường và kiểm tra unique cả ở Service lẫn DB. **Cú pháp hợp lệ không chứng minh hộp thư tồn tại**; Admin cần xác minh email thật trước khi cấp/sửa.
- Số di động VN 10 số, đầu 03/05/07/08/09; chấp nhận +84, dấu cách/chấm/gạch ngang và chuẩn hóa. Không thể xác nhận số đang hoạt động hoặc thuộc người nhận chỉ qua regex. Phone là thông tin liên hệ, không bắt buộc unique giữa các tài khoản.
- Mật khẩu mới 12–64 ký tự, chữ hoa/thường/số/ký tự đặc biệt, không khoảng trắng/ký tự điều khiển, tối đa 72 byte UTF-8; xác nhận phải khớp; lưu BCrypt. Không hiển thị lại mật khẩu khi form lỗi.
- Lý do 5–200 ký tự, không xuống dòng/ký tự điều khiển; ID dương, revision không âm, trạng thái/role phải hợp lệ. Không nhận role, username actor hoặc entity từ dữ liệu người dùng để quyết định quyền.
- Sai mật khẩu xác nhận 5 lần: tạm chặn xác nhận CRUD 15 phút trên app đang chạy; trạng thái này ở bộ nhớ, không chia sẻ giữa nhiều app server. Không ghi mật khẩu sai vào log.
- CSRF bật cho các POST quản trị; không thực hiện xóa bằng GET. Lỗi form hiển thị thông báo để sửa; xung đột 409, truy cập sai 403, lỗi database 503, không trả chi tiết SQL/bí mật.

Trang `/admin/tai-khoan` cũ vẫn là lối cấp/đổi quyền nhân sự nội bộ. CRUD cả bốn vai trò nằm ở trang mới nói trên; không xóa API cũ của phần Thắng trong đợt này.

## Aiven dùng chung

Đã chạy `UpgradeAivenAccounts --apply` trên Aiven: thêm `app_user.revision BIGINT DEFAULT 0`, `locked_by_admin BIT DEFAULT 0`, `access_version BIGINT DEFAULT 0`, đều NOT NULL. Không thêm bảng, vẫn **12 bảng**; không xóa/ghi đè tài khoản cũ. Các thành viên cần pull cùng code rồi restart; code cũ không kiểm tra/tăng các trường này nên không bảo đảm cơ chế mới khi chạy song song.

Database mới trống dùng công cụ InitializeAivenDatabase như SETUP. Database khác đã có app_user cũ: dừng app nhóm, tạo classpath rồi chạy đúng một lần (có thể chạy lại an toàn):

```powershell
Set-Location E:\Lap_Trinh_Web\UTEEXPRESS
mvn -q "-DskipTests" compile dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt"
$taskClasspath = "target/classes;" + (Get-Content target/runtime-classpath.txt -Raw).Trim()
java "-Dfile.encoding=UTF-8" -cp $taskClasspath vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenAccounts --apply
java "-Dfile.encoding=UTF-8" -cp $taskClasspath vn.edu.hcmute.uteexpress.tool.database.CheckAivenConnection
```

Chỉ dùng Aiven của nhóm; không chạy MySQL local hoặc DROP/TRUNCATE. Công cụ upgrade dùng TLS VERIFY_IDENTITY, khóa migration, chỉ thêm cột thiếu, từ chối cột hiện có sai kiểu. MySQL DDL tự commit; lỗi giữa chừng cần kiểm tra trước khi chạy lại.

## Checklist demo

1. Admin tạo từng role; Admin/Manager phải đổi mật khẩu lần đầu, User/Shipper dùng `/dang-nhap`. Chưa tự gửi mail bàn giao mật khẩu; dùng kênh riêng.
2. Manager không có nút CRUD; gõ trực tiếp URL và gửi POST có CSRF vẫn 403. User/Shipper cũng không vào được khu quản trị.
3. Thử thiếu/sai/chuỗi quá dài cho mọi trường, username/email trùng khác hoa thường, phone sai đầu/sai số, role giả, mật khẩu yếu/lệch/>72 byte, lý do quá ngắn và revision thiếu/sai.
4. Hai tab mở cùng tài khoản: tab đầu sửa, tab sau sửa/xóa/reset phải báo xung đột; tải lại rồi mới gửi tiếp.
5. Đăng nhập User/Shipper ở trình duyệt khác, Admin khóa/reset; request tiếp theo phải hết phiên. Mở khóa rồi thử cookie cũ: không hồi sinh. OTP còn lưu trước khóa không mở lại được.
6. Xóa một tài khoản demo mới chưa có dữ liệu: không còn trong danh sách/đăng nhập được; vẫn thấy nhật ký xóa. Sai username xác nhận hoặc mật khẩu Admin thì không xóa.
7. Tài khoản đã tạo đơn/địa chỉ/giỏ đơn/bằng chứng không xóa được. Xóa/sửa chính Admin đang đăng nhập bị chặn; Shipper có đơn chưa hoàn tất không khóa được.
8. Ngắt mạng Aiven: báo lỗi, không dùng database khác và không bỏ qua kiểm tra quyền.

Kiểm thử tự động dùng tài khoản QA mới và rollback; không dùng chức năng xóa để test trên tài khoản nhóm có sẵn. Chưa thay thế kiểm thử hai trình duyệt/nhiều máy, stress test hay xác minh email/điện thoại thực tế. Xem [lệnh commit](COMMIT_PLAN_2026_10_10.md); chưa commit/push tự động.

Kết quả bàn giao: `mvn -q clean package` đạt **297 tests, 0 failures, 0 errors, 0 skipped**. Bao gồm render/binding/CSRF của trang CRUD, chặn các role khác, CRUD cả bốn role và chặn khóa Shipper có đơn trên Aiven thật (rollback). Đã chạy lại migration idempotent, kiểm tra TLS/12 bảng/3 cột tài khoản; JAR không chứa cấu hình mật khẩu local hoặc CA/truststore. Đây là các trường hợp đã kiểm tra, không phải cam kết không thể phát sinh bất kỳ lỗi nào.
