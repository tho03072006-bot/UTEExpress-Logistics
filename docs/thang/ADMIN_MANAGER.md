# Manager và Admin thuộc phần Thắng

## Phạm vi

Đợt này bổ sung tài khoản nội bộ được cấp, phân cấp Admin/Manager, validation, bảo vệ phiên và tra soát đơn hàng. Không sửa `AppUser.java`, `Order.java`, repository dùng chung, `SecurityConfig.java`, `AuthServiceImpl.java`, controller/template của Guest/User/Shipper hoặc các script DB có sẵn.

Thêm bảng riêng `staff_account` với khóa ngoại đến `app_user.id`. Theo yêu cầu mở rộng về database chung, cấu hình ứng dụng chuyển sang Aiven MySQL mới; công cụ khởi tạo đủ bảng từ mapping hiện có, không sửa entity của Thọ/Tài. Khi Admin cấp tài khoản, ứng dụng thêm một dòng người dùng mới dành cho nhân sự nội bộ và hồ sơ staff tương ứng trong cùng giao dịch. Không sửa/nâng quyền tài khoản USER/SHIPPER hiện có. Các thao tác quản trị chỉ nhận ID của hồ sơ staff, không nhận ID người dùng bất kỳ.

Cấu hình SQL Server cũ đã sao lưu vào file local được gitignore; cấu hình chung nay chỉ dùng Aiven. Mail/JWT giữ nguyên. Chưa chạy SQL, chưa cấp tài khoản thật, chưa khởi chạy ứng dụng với database thật.

## Quyền đã triển khai

| Chức năng | Manager | Admin |
| --- | --- | --- |
| Tổng quan vận hành | Có | Có |
| Xem đơn toàn hệ thống, lọc chính xác theo mã/trạng thái/ngày, phân trang | Có | Có |
| Đổi mật khẩu chính mình, xác nhận mật khẩu hiện tại | Có | Có |
| Cấp tài khoản Manager/Admin mới | Không | Có |
| Xem danh sách tài khoản nội bộ | Không | Có |
| Khóa/mở, chuyển cấp quyền Manager/Admin của tài khoản khác | Không | Có |
| Cấp lại mật khẩu cho nhân sự khác | Không | Có |
| Tự thay đổi cấp quyền/trạng thái, xóa tài khoản | Không | Không |

Những tính năng chưa được thực hiện trong đợt này: phân công Shipper, CRUD bảng giá/khuyến mãi/khu vực, biểu đồ doanh thu và hoàn thiện JWT. Không hiển thị nút thao tác giả cho các chức năng này.

## Tài khoản nội bộ

- Đường vào: `/noi-bo/dang-nhap`.
- Admin: `/admin/trang-chu`, `/admin/tai-khoan`.
- Manager và Admin: `/manager/trang-chu`, `/manager/don-hang`.
- Mật khẩu: `/noi-bo/doi-mat-khau`; đăng xuất bằng POST `/noi-bo/dang-xuat`.
- Đăng ký công khai vẫn chỉ tạo USER. Khu nội bộ yêu cầu cả hồ sơ `staff_account` và principal nội bộ hợp lệ; tài khoản từng nâng quyền bằng SQL sau OTP không tự động được vào.
- Tài khoản được cấp có BCrypt, `enabled=true`, không tạo mã OTP, cần đổi mật khẩu lần đầu. Sau khi cấp lại mật khẩu, cũng bắt buộc đổi.
- Chặn kích hoạt và đặt lại mật khẩu bằng OTP công khai cho ADMIN/MANAGER. Lớp kiểm tra nằm riêng trong `config.admin`; luồng OTP của USER/SHIPPER không bị sửa. Phản hồi OTP vẫn mang thông báo chung để tránh tiết lộ email nhân sự.
- Phiên nội bộ kiểm tra database trên mỗi yêu cầu: active/enabled, vai trò, phiên bản truy cập và dấu vân tay của hash mật khẩu. Khóa, đổi quyền hoặc đổi mật khẩu khiến phiên cũ hết hiệu lực ở yêu cầu tiếp theo.
- Mất kết nối khi kiểm tra quyền: từ chối truy cập với 503, không bỏ qua kiểm tra.
- CSRF bật cho mọi POST nội bộ; Manager bị chặn cả GET lẫn POST vào Admin dù tự gõ URL.

## Database Aiven và cấp Admin đầu tiên

Làm theo [AIVEN_MYSQL.md](AIVEN_MYSQL.md): cấu hình CA/TLS, khởi tạo schema chung một lần, chạy công cụ cấp Admin không OTP và cấu hình cả ba máy cùng database. Không chạy SQL Server hoặc script nâng quyền tài khoản OTP cũ.

Công cụ `ProvisionInitialAdmin` chỉ tạo khi chưa có hồ sơ staff, không ghi đè hoặc sửa tài khoản cũ. Đăng nhập tài khoản mới tại `/noi-bo/dang-nhap`, đổi mật khẩu tạm và đăng nhập lại; sau đó Admin cấp Manager/Admin qua giao diện. Bàn giao mật khẩu qua kênh riêng, không lưu trong issue/PR.

Nếu Admin cuối cùng quên mật khẩu, cần quy trình phục hồi do người quản trị DB và nhóm duyệt; công cụ cấp đầu tiên không vượt qua kiểm tra hồ sơ đã tồn tại.

## Validation và lỗi đã xử lý

- Tên đăng nhập: trim, lowercase, 4–50 ký tự, bắt đầu bằng chữ; chỉ chữ thường/số/dấu chấm/gạch ngang/gạch dưới. Username/email trùng kiểm tra không phân biệt hoa thường, kể cả tài khoản đăng ký chưa kích hoạt.
- Họ tên: 2–100 ký tự, cần có chữ, hỗ trợ Unicode tiếng Việt; email chuẩn, có tên miền, tối đa 100 ký tự.
- Cấp quyền chỉ nhận ADMIN hoặc MANAGER; DTO không bind thẳng vào entity.
- Mật khẩu mới: 12–64 ký tự, chữ hoa/thường, số và ký tự đặc biệt; không khoảng trắng; tối đa 72 byte UTF-8 do BCrypt; hai ô phải khớp; không tái sử dụng mật khẩu hiện tại.
- Không hiển thị lại password/hash trên trang lỗi hoặc danh sách. Cấp lại mật khẩu chỉ dành cho tài khoản khác đang hoạt động.
- Thiếu/sai/âm ID hoặc revision, không tồn tại hồ sơ staff, form đã cũ, không có gì thay đổi, dữ liệu trùng phát sinh đồng thời: báo lỗi rõ ràng và không ghi đè.
- Thao tác thay quyền/khóa dùng khóa pessimistic theo cùng thứ tự và kiểm tra lại actor trong transaction. Không tự khóa/hạ quyền. Không làm mất Admin hoạt động cuối cùng. Hồ sơ có revision để chặn cập nhật từ form cũ.
- Sai đăng nhập 5 lần trong 15 phút: chặn tạm theo username. Giới hạn lưu 10.000 tên để tránh bộ nhớ tăng vô hạn; khi đầy, tên chưa được theo dõi bị chặn tạm. Giới hạn này ở bộ nhớ của một tiến trình; cần kho dùng chung/rate limit ở proxy nếu triển khai nhiều instance, và trạng thái giới hạn mất khi restart.
- Tra soát đơn: mã tối đa 20 ký tự, chỉ chữ/số/gạch ngang, tìm chính xác; trạng thái enum hợp lệ; ngày bắt đầu không sau ngày kết thúc; không tương lai; từ năm 2000, tối đa 366 ngày; 20 dòng/trang, thứ tự ổn định theo ngày tạo và ID.
- Ngày kết thúc bao gồm cả ngày, nhờ cận trên là đầu ngày hôm sau. Đơn chưa có Shipper/cước vẫn hiển thị được.
- Không đưa chi tiết SQL hoặc bí mật cấu hình ra màn hình khi lỗi. Log quản trị ghi người thực hiện, ID và loại thao tác, không ghi mật khẩu hoặc OTP.

Không khẳng định đã bao phủ mọi lỗi có thể xảy ra. Khóa đồng thời thực tế, DDL/JDBC trên Aiven MySQL và trình duyệt với dữ liệu thật cần kiểm thử tích hợp riêng.

## Kiểm thử

Đã chạy bằng Java 17:

```powershell
mvn -B -o "-Dtest=*,!UteexpressApplicationTests" test
```

Kết quả offline: 142 tests, 0 failure, 0 error, gồm 103 test mới và 39 test sẵn có. Sau khi khởi tạo Aiven, chạy toàn bộ `mvn -B test`: 143 tests, 0 failure, 0 error; `UteexpressApplicationTests` khởi động Spring Boot và Hibernate `validate` thành công với MySQL 8.4 thật. Chưa chạy trình duyệt hoặc tạo tài khoản thật.

Sau khi PR được review/approve và merge vào develop, kiểm tra tích hợp trên database Aiven đã chuẩn bị:

1. Cấp Admin đầu tiên trực tiếp; xác nhận không gửi OTP, bắt buộc đổi mật khẩu.
2. Admin cấp Manager; Manager đăng nhập đúng màn hình, không vào được Admin bằng URL hoặc POST.
3. Admin cấp Admin thứ hai; kiểm tra tự khóa/hạ quyền bị chặn.
4. Mở hai trình duyệt; khóa/hạ quyền/cấp lại mật khẩu cho tài khoản đang đăng nhập; phiên cũ bị thu hồi.
5. Mở hai tab chỉnh cùng tài khoản; tab gửi sau với revision cũ bị từ chối.
6. Thử trùng email/username và form sai; không có dòng AppUser mồ côi nếu tạo StaffAccount thất bại.
7. Kiểm tra đơn đúng ngày biên, không có kết quả, phân trang, ký tự tiếng Việt.
8. Chạy lại các luồng Guest/User/Shipper của nhóm, đặc biệt đăng ký OTP khách hàng và giao hàng.

Xem `COMMIT_PLAN.md` để stage theo nhóm, commit ngắn gọn và push chỉ lên `feature_Thang`.
