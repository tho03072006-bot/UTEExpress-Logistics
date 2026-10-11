# Nâng cấp Manager + Admin — 10/10/2026

## Phạm vi và cơ sở lựa chọn

Thắng phụ trách Manager/Admin. Yêu cầu mới cho phép sửa shared code/schema và phần thành viên khác **khi cần cho chức năng dùng chung**. Thay đổi lần này tập trung vào điều phối, khuyến mãi, cước và báo cáo; giữ quy trình giao hàng/bằng chứng giao và thanh toán hiện có.

Tham khảo các chức năng công khai:

- [GHN — hướng dẫn web khách hàng](https://ghn.vn/blogs/tin-tuc-ghn/huong-dan-trai-nghiem-phien-ban-moi-web-khach-hang-ghn-vn): quản lý/tra cứu đơn và tổ chức quyền truy cập, đối soát. UTEExpress chọn điều phối, tra soát và báo cáo phù hợp dữ liệu đang có.
- [J&T — bảng cước công khai](https://jtexpress.vn/storage/app/uploads/public/674/aff/dd9/674affdd9a609069912582.pdf): cước là một cấu hình nghiệp vụ cần quản lý nhất quán; không sao chép giá trong PDF làm giá hiện tại của UTEExpress.
- [Viettel Post — quy trình giải quyết khiếu nại](https://viettelpost.com.vn/wp-content/uploads/2023/12/Quy-trinh-giai-quyet-khieu-nai-trinh.pdf): tính truy vết và quy trình xử lý cần minh bạch; UTEExpress bổ sung nhật ký thao tác trước khi xây dựng phân hệ khiếu nại.

Phân cấp Admin > Manager là **thiết kế của đồ án**, không phải mô tả quyền nhân viên nội bộ của các hãng. Chưa tích hợp API GHN/J&T, đối soát tiền shop, khiếu nại, vùng/tuyến hoặc biểu đồ lịch sử.

## Chức năng theo nhóm

| Nhóm | Manager | Admin | Điểm kiểm soát |
| --- | --- | --- | --- |
| Điều phối `/manager/phan-cong` | Có | Có | Chỉ PENDING_PICKUP, Shipper đang hoạt động, ID hợp lệ, lý do 5–200 ký tự, revision còn mới |
| Khuyến mãi `/manager/khuyen-mai` | Có | Có | Mã duy nhất, tiền nguyên, hạn/lượt hợp lệ; mã đã dùng không đổi điều kiện tài chính |
| Báo cáo `/manager/bao-cao` | Có | Có | Khoảng ngày tối đa 366 ngày, không tương lai; CSV chỉ số liệu tổng hợp |
| Xem cước `/manager/bang-cuoc` | Có | Có | Cùng cấu hình dùng để báo giá và tạo đơn |
| Sửa cước `/admin/bang-cuoc` | Không | Có | Không âm/lẻ VND/vượt trần; không cùng bằng 0, có lý do và revision |
| Nhật ký `/admin/nhat-ky` | Không | Có | Chỉ đọc, phân trang, không lưu mật khẩu/địa chỉ khách |
| Cấp tài khoản nội bộ | Không | Có | Giữ cơ chế cấp không OTP, đổi mật khẩu lần đầu, thu hồi phiên và bảo vệ Admin cuối cùng |
| CRUD tài khoản Admin/Manager/User/Shipper | Không | Có | Xác nhận thao tác, revision, bảo vệ dữ liệu liên kết; xem ADMIN_ACCOUNT_CRUD.md |

Mọi POST nội bộ dùng CSRF. Chỉ principal nội bộ được cấp mới vào được khu này; tự gửi actor/role/status trong form không tạo thêm quyền. Service kiểm tra lại quyền từ database.

### 1. Điều phối

Danh sách chờ lấy sắp theo ngày tạo và ID. Danh sách shipper sắp theo số đơn đang xử lý; hiển thị tối đa 100 shipper/trang và có chuyển trang riêng.

Không nhận nhân sự USER/MANAGER/ADMIN hoặc Shipper khóa. Không phân công lại sau khi lấy hàng, không đổi sang DELIVERED và không bỏ qua bằng chứng giao. Không cho lưu lại đúng shipper hiện tại. Khóa dòng và version chặn hai quản lý ghi đè; version cũng bảo vệ xung đột với User hủy đơn/Shipper cập nhật.

### 2. Khuyến mãi

Mã được trim và viết hoa, 3–30 chữ/số/gạch ngang/gạch dưới; mô tả tối đa 255 ký tự, không ký tự điều khiển. Giảm phần trăm 1–100 và bắt buộc có trần; giảm cố định không dùng trần. Tiền VND nguyên, tối đa 100 triệu; cước tối thiểu không âm. Tổng lượt 1–1.000.000, không thấp hơn số đã dùng.

Ngày kết thúc sau ngày bắt đầu, tối đa 366 ngày, năm 2000–2100; mã mới phải còn thời hạn. Không đổi tên mã đã tạo. Mã đã dùng chỉ đổi mô tả/trạng thái/tổng lượt, không đổi giá trị, kiểu giảm, trần, cước tối thiểu và thời hạn. Không có xóa mã.

Có nút bật/tắt riêng để quản lý cả mã legacy chưa có giới hạn/ngày. Không bật mã hết hạn hoặc hết lượt. Sửa form quá hạn/cũ báo lỗi thay vì ghi đè.

Tạo đơn, thanh toán và tăng lượt khuyến mãi nằm trong một transaction. Khi dùng mã, khóa dòng từ lúc kiểm tra đến lúc tăng lượt, tránh hai đơn cùng lấy lượt cuối. Nếu tạo thanh toán lỗi, đơn và lượt mã không được commit riêng lẻ.

### 3. Cước vận chuyển

Công thức demo: cơ bản + phí/kg × khối lượng, làm tròn HALF_UP đến VND nguyên. Chỉ khối lượng hữu hạn >0 đến 100 kg.

Admin đặt cơ bản 0–100 triệu và phí/kg 0–5 triệu; không được cùng bằng 0. Manager chỉ xem. Không có fallback về mức phí hardcode nếu thiếu bảng cước.

Báo giá Guest và tạo đơn User dùng cùng ShippingTariffService. Đơn/payment đã có giữ tiền tại thời điểm tạo; không tính lại sau khi đổi giá. Migration giữ mức demo cũ: 15.000/25.000/40.000 + 3.000/kg, không phải bảng giá hãng vận chuyển thật.

### 4. Báo cáo / CSV

- Số đơn theo **ngày tạo**, phân nhóm theo trạng thái hiện tại; không giả định đây là lịch sử trạng thái tại ngày kết thúc.
- Tỷ lệ thành công = DELIVERED / (DELIVERED + FAILED + RETURNED); không tính CANCELLED hoặc đơn đang xử lý; 0% khi chưa có mẫu đã kết thúc.
- Cước đã trả theo **ngày thanh toán**, sử dụng amount đã trừ khuyến mãi; không phải tiền COD hàng hóa của shop.
- CSV UTF-8 chỉ gồm ngày, enum và số liệu tổng hợp; không xuất thông tin khách, chuỗi nhập liệu có thể thành công thức Excel hoặc toàn bộ danh sách đơn.

### 5. Nhật ký

Các thao tác cấp/đổi quyền/khóa/cấp lại mật khẩu/đổi mật khẩu, phân công, khuyến mãi và cước được lưu kèm actor, thời điểm, đối tượng và chi tiết cần thiết. Ghi log cùng transaction; thao tác thất bại/rollback không để nhật ký “thành công” riêng. Nhật ký chỉ bắt đầu từ bản nâng cấp, không tự tái dựng lịch sử trước đó. Công cụ cấp Admin đầu tiên ngoài ứng dụng chưa ghi nhật ký này.

## Thay đổi shared code/schema

- `Order`, `PromoCode`: thêm revision chống cập nhật đè, migration điền 0 cho dòng cũ.
- `AppUser`: thêm revision, locked_by_admin và access_version; CRUD cả bốn role chỉ Admin; OTP và phiên User/Shipper tôn trọng khóa/xóa/reset. Xem [hướng dẫn CRUD](ADMIN_ACCOUNT_CRUD.md).
- `shipping_tariff`, `audit_event`: hai bảng mới; schema chung từ 10 lên 12 bảng.
- `OrderServiceImpl`: đọc cước Aiven, validate tại Service, transaction tạo/hủy đơn.
- `OrderCreateRequest`: giới hạn độ dài tên/địa chỉ, Unicode tên, số di động VN (+84 được chuẩn hóa), cân nặng hữu hạn/tối đa 100 kg, giới hạn mã khuyến mãi.
- `PromoCodeRepository/Service`: khóa khi kiểm tra và dùng mã, giữ giới hạn lượt khi đồng thời.
- Xử lý lỗi chung không trả chi tiết SQL/exception/URL kết nối; lỗi nhập liệu 400, xung đột 409, lỗi truy cập 403, lỗi database 503. Form nhập sai có thể được trả lại HTTP 200 với thông báo validation để sửa; không gọi Service ghi dữ liệu.
- Thống nhất giờ Việt Nam trong Clock/JVM và session MySQL (+07:00). [Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html) yêu cầu bật forceConnectionTimeZoneToSession để đặt múi giờ session; chỉ khai báo connectionTimeZone không tự đổi session.
- Runtime và công cụ database chỉ Aiven, TLS VERIFY_IDENTITY. Không đổi cấu hình CA/password của người dùng; không ghi bí mật vào JAR/Git.

Đã gỡ mẫu profile SQL Server/MonsterASP, công cụ backup SQL Server cũ và bản cấu hình SQL Server riêng từng lưu. Giữ nguyên database/bảng vật lý trên máy; không dùng mật khẩu local, không sao lưu hoặc DROP database local. Giữ các script SQL Server lịch sử và file không liên quan từ trước; không xóa bừa thư mục thành viên.

## Nâng cấp database của nhóm

`UpgradeAivenOperations --apply` đã chạy trên Aiven ngày 10/10/2026, bổ sung bảng/revision và cước mặc định. Công cụ chạy lại không xóa/ghi đè dữ liệu hiện có; không tự chạy khi app khởi động. MySQL DDL tự commit, nên không hứa rollback cấu trúc khi migration bị lỗi giữa chừng.

Các thành viên pull cùng phiên bản sau khi merge develop, kiểm tra HOST/DATABASE chung, dùng truststore của máy mình và restart. Không chạy code cũ song song lâu dài: code cũ chưa tăng revision và còn dùng giá hardcode, nên không bảo đảm cùng cơ chế kiểm soát cập nhật.

## Kịch bản demo / kiểm thử thủ công

1. Manager đăng nhập nội bộ; thử URL/POST Admin sửa cước và nhật ký: phải bị chặn. Admin mở được cả hai khu.
2. Chuẩn bị hai Shipper đang hoạt động; phân công đơn chờ lấy, kiểm tra đúng shipper nhìn thấy đơn. Đổi shipper trước lấy hàng; sau PICKED_UP phải bị chặn.
3. Hai tab mở cùng đơn: tab đầu phân công, tab sau gửi revision cũ phải bị từ chối. Thử đơn bị User hủy hoặc Shipper lấy trong lúc Manager đang mở trang.
4. Tạo mã giảm phần trăm/cố định; thử 101%, tiền âm/lẻ, mã trùng khác hoa thường, ngày ngược/hết hạn, mô tả quá dài. Dùng mã ở User; số lượt tăng đúng; sau sử dụng sửa giá phải bị chặn nhưng tắt mã vẫn được.
5. Thử lượt cuối bằng hai trình duyệt tạo đơn đồng thời. DB integration đã kiểm tra đường khóa/tăng lượt; chưa có stress test nhiều máy nên cần kịch bản này trước demo.
6. Admin sửa cước, Guest báo giá và User tạo đơn dùng giá mới; đơn cũ giữ nguyên. Manager không sửa được dù tự gửi POST.
7. Báo cáo ngày biên, khoảng trống, >366 ngày và ngày tương lai; kiểm tra CSV chỉ số liệu tổng hợp, không thông tin cá nhân.
8. Nhật ký hiển thị thao tác thành công; kiểm tra thiếu/sai dữ liệu không tạo đơn/cước/khuyến mãi riêng lẻ.
9. Kiểm thử lại Guest/User/Shipper: OTP, tạo đơn/giỏ đơn, ví mô phỏng, hủy, tiến trình giao, bằng chứng giao và WebSocket.
10. Tắt mạng Aiven: báo lỗi, không xuất hiện database local hoặc cho qua kiểm tra quyền.

Không khẳng định loại bỏ mọi lỗi. Kiểm tra tự động bao phủ các trường hợp được liệt kê; demo trình duyệt nhiều máy, tải lớn và gửi SMTP thật vẫn cần kiểm tra trong môi trường nhóm. Xem COMMIT_PLAN_2026_10_10.md để commit theo từng chức năng.

