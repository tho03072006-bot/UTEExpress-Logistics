# Tài khoản demo UTEExpress

Cập nhật 12/10/2026. Có **4 role tài khoản**: `USER`, `SHIPPER`, `MANAGER`, `ADMIN`.
**Guest** là khách chưa đăng nhập, không phải một role tài khoản.

Mật khẩu thống nhất cho các tài khoản dưới đây: **`123456`**.

| Role | Tên đăng nhập | Cách vào demo |
|---|---|---|
| `USER` | `thotest`, `huongshop`, `minhstore`, `lanboutique`, `newuser1` | [Đăng nhập khách hàng](http://localhost:8080/dang-nhap) |
| `SHIPPER` | `shipper1`, `shipper2`, `shipper3` | Mở [trang Shipper](http://localhost:8080/shipper/trang-chu), rồi đăng nhập tại trang được chuyển tới |
| `MANAGER` | `manager1` | [Đăng nhập nội bộ](http://localhost:8080/noi-bo/dang-nhap) |
| `ADMIN` | `admin` | [Đăng nhập nội bộ](http://localhost:8080/noi-bo/dang-nhap) |

Shipper nên mở `/shipper/trang-chu` trước để ứng dụng lưu trang đích và quay lại đúng khu vực
sau khi đăng nhập. Guest xem [trang chủ](http://localhost:8080/) mà không cần tài khoản.

Tài khoản `chuakichhoat` thuộc role `USER`, mật khẩu `123456`, **vẫn bị vô hiệu hóa**.
Dùng tài khoản này để demo trường hợp bị từ chối đăng nhập vì chưa kích hoạt.

## Đồng bộ mật khẩu demo

Từ thư mục gốc project, dùng cấu hình database local đã hướng dẫn trong [SETUP](../SETUP.md).
Chạy main class `vn.edu.hcmute.uteexpress.tool.demo.ResetDemoAccounts` với tham số
`--apply` để áp dụng việc đồng bộ. Trong Eclipse, chọn class → **Run As → Java Application**,
đặt **Program arguments** là `--apply` trong Run Configuration.

Công cụ dùng BCrypt để lưu mật khẩu, tạo bản sao lưu local tại
`config/demo-account-backups/` trước khi cập nhật. Thư mục sao lưu đã được bỏ qua bởi Git;
không đưa bản sao lưu hoặc cấu hình database/SMTP vào repository.

Đây là thao tác chạy thủ công. Ứng dụng không tự đặt lại mật khẩu khi khởi động.
Mật khẩu ngắn `123456` dành cho dữ liệu demo; quy tắc mật khẩu của các form đăng ký,
quên mật khẩu và đổi mật khẩu vẫn giữ nguyên.
