# UTEExpress — quản lý chuỗi giao nhận logistics

Đề tài 10, học phần Lập trình Web (WEBPR330479) — HCMUTE. Ứng dụng hỗ trợ người gửi tạo và theo dõi vận đơn, Shipper giao hàng, Manager tra soát vận hành và Admin quản lý tài khoản nội bộ.

**Cập nhật 12/10/2026:** đã sửa validation, OTP, transaction, quyền WebSocket/tệp giao hàng và tích hợp MoMo TEST. Có 334 test pass; xem [bản sửa và kiểm chứng](docs/FIX_VERIFICATION_2026-10-12.md). [Phạm vi đã chốt](docs/PROJECT_SCOPE_2026-10-12.md): nhận/giao toàn quốc qua mạng mô phỏng dưới một trung tâm điều phối, 5 tác nhân và 4 role tài khoản.

## Công nghệ và kiến trúc

- Java 17+, Maven, Spring Boot 3.3.4.
- Spring MVC + Thymeleaf + Bootstrap 5.3.3; bố cục chung ở `templates/fragments/base.html`.
- Spring Data JPA + **MySQL trên Aiven**, TLS xác minh CA và hostname bằng `sslMode=VERIFY_IDENTITY`, truststore PKCS12. Hibernate chỉ kiểm tra schema bằng `ddl-auto=validate`.
- Spring Security + BCrypt; đăng nhập người dùng và nhân sự nội bộ dùng các security chain riêng.
- Spring Mail cho OTP đăng ký, quên mật khẩu và đổi email.
- WebSocket STOMP/SockJS cho cập nhật trạng thái vận đơn. Có thư viện và lớp hỗ trợ JWT, nhưng luồng xác thực JWT chưa hoàn thiện.

Mã nguồn theo luồng Controller → Service → Repository → Entity. User, Shipper, Manager và Admin dùng chung một datasource Aiven. Ứng dụng hiện không dùng profile `cloud` hoặc SQL Server.

## Chức năng hiện có và phần còn thiếu

| Vai trò | Đã có trong mã nguồn |
| --- | --- |
| Guest | Trang chủ, tra cứu vận đơn, ước tính cước, số liệu và đánh giá dịch vụ |
| User | Đăng ký/kích hoạt OTP, đăng nhập/đăng xuất, khôi phục mật khẩu, hồ sơ và sổ địa chỉ; tạo đơn, giỏ đơn lưu database, lọc lịch sử, theo dõi trạng thái, mã giảm giá, đánh giá kèm tệp, in vận đơn |
| Shipper | Danh sách đơn đã được phân công, chi tiết và cập nhật trạng thái, bằng chứng giao hàng, thống kê theo kỳ |
| Manager | Bảng tổng quan, tra soát đơn toàn hệ thống theo mã/trạng thái/ngày, phân trang |
| Admin | Các quyền Manager; cấp, tìm kiếm, khóa/mở, đổi quyền và cấp lại mật khẩu cho **tài khoản nội bộ** |

Các giới hạn cần biết khi demo:

- **MoMo TEST đã kết nối cổng thử**, hỗ trợ QR và trang ví/thẻ; chỉ xác nhận cước qua query máy chủ hoặc IPN có chữ ký. VNPay chưa tích hợp và không hiển thị như lựa chọn hoạt động. COD hiện là thu cước vận chuyển, chưa là thu hộ giá trị hàng hóa. Xem [hướng dẫn MoMo](docs/MOMO_TEST_PAYMENT.md).
- `JwtAuthFilter` còn là khung chờ triển khai; việc có dependency JJWT chưa đáp ứng yêu cầu sử dụng JWT.
- Chưa có giao diện Manager phân công Shipper; CRUD bảng giá, khuyến mãi, khu vực và nhà vận chuyển còn thiếu. Giá cước còn gán trong mã nguồn.
- Quản trị tài khoản nội bộ hiện chưa thay thế chức năng quản lý toàn bộ khách hàng và Shipper.
- Chưa có Vendor/shop, danh mục/sản phẩm, yêu thích và sản phẩm đã xem theo danh sách yêu cầu thương mại điện tử. Nhóm cần có xác nhận của giảng viên về cách quy đổi các yêu cầu đó sang đề tài logistics.
- Tài liệu phân công nội bộ không thay thế hồ sơ khảo sát, thiết kế hệ thống và bằng chứng kiểm thử theo mẫu của giảng viên.

## Chạy trên máy thành viên

Cài JDK 17 hoặc 21 và Maven; kiểm tra `java -version` và `mvn -version` đều dùng JDK phù hợp. Chạy các lệnh từ thư mục gốc chứa `pom.xml`.

1. Copy `config/aiven-local.properties.example` thành `config/aiven-local.properties`, điền các mục `AIVEN_*` bằng thông tin được bàn giao riêng trong nhóm.
2. Lưu CA của dịch vụ vào `config/certificates/ca.pem`; nhập CA vào truststore PKCS12 bằng `keytool`, theo [hướng dẫn Aiven](docs/thang/AIVEN_MYSQL.md). Điền URL truststore và mật khẩu trong file local. Không tắt xác minh TLS.
3. Copy `src/main/resources/application-local.properties.example` thành `src/main/resources/application-local.properties`, cấu hình SMTP để gửi OTP thật và khóa JWT riêng. Không đặt lại `spring.datasource.*` trong file này.
4. Chạy ứng dụng:

```powershell
mvn spring-boot:run
```

Mở [trang chủ](http://localhost:8080). Đăng nhập người dùng ở `/dang-nhap`; nhân sự nội bộ ở `/noi-bo/dang-nhap`. Không thêm profile `cloud` hoặc override datasource SQL Server cũ.

CA, truststore, cấu hình local, tệp tải lên và bản sao database được Git bỏ qua. Bàn giao tài khoản qua kênh riêng; không đưa mật khẩu, OTP, khóa JWT, SMTP App Password vào commit, issue, PR hay ảnh chụp. Không khởi tạo lại schema đã có trên Aiven hoặc bật `ddl-auto=update/create` trên database chung.

Chi tiết cấu hình, xử lý lỗi và cấp Admin đầu tiên: [SETUP.md](SETUP.md), [AIVEN_MYSQL.md](docs/thang/AIVEN_MYSQL.md), [ADMIN_MANAGER.md](docs/thang/ADMIN_MANAGER.md).

## Tài khoản demo

Mật khẩu thống nhất cho tất cả tài khoản demo thuộc `USER`, `SHIPPER`, `MANAGER`, `ADMIN`: **`123456`**.

| Tên đăng nhập | Role | Đường vào |
| --- | --- | --- |
| `thotest`, `huongshop`, `minhstore`, `lanboutique`, `newuser1` | USER | `/dang-nhap` |
| `shipper1`, `shipper2`, `shipper3` | SHIPPER | Mở `/shipper/trang-chu` trước khi đăng nhập |
| `manager1` | MANAGER | `/noi-bo/dang-nhap` |
| `admin` | ADMIN | `/noi-bo/dang-nhap` |

`chuakichhoat` cũng dùng mật khẩu `123456`, nhưng giữ trạng thái chưa kích hoạt để thử đăng nhập bị từ chối.

Xem [hướng dẫn tài khoản demo](docs/DEMO_ACCOUNTS.md) để đồng bộ database bằng công cụ chạy thủ công, sao lưu dữ liệu đăng nhập và bổ sung hồ sơ nội bộ cho Manager/Admin. Mật khẩu trong database được lưu bằng BCrypt; ứng dụng không tự reset khi khởi động.

Mã giảm giá: `UTE10` (giảm 10%), `FREESHIP15` (giảm 15.000đ cho đơn từ 30.000đ),
`HETHAN` (đã hết hạn), `DATAT` (đã tắt) — hai mã sau dùng để thử ca lỗi.

## Kiểm thử

```powershell
# Unit/MVC test, không cần kết nối Aiven
mvn test

# Bao gồm kiểm thử tích hợp với Aiven thật, cần đủ cấu hình local/TLS
mvn -Paiven-integration verify
```

Maven cần tải dependency trong lần đầu; sau khi có đủ cache mới có thể chạy hoàn toàn ngoại tuyến. Kết quả unit test không xác nhận việc gửi email thật, thanh toán qua nhà cung cấp, quyền truy cập nhiều tài khoản hoặc toàn bộ thao tác trên trình duyệt. Đối chiếu các luồng này trong báo cáo kiểm thử trước khi demo.

## Phân công và Git

| Thành viên | Nhánh | Phạm vi phụ trách |
| --- | --- | --- |
| Thọ | `feature_Tho` | Guest + User, luồng tạo và quản lý đơn |
| Tài | `feature_Tai` | Shipper, theo dõi và bằng chứng giao hàng, WebSocket |
| Thắng | `feature_Thang` | Manager + Admin, quản trị và cấu hình database chung |

Nhóm có kế hoạch 3 thành viên; hồ sơ nộp cần bổ sung đầy đủ họ tên, MSSV và bằng chứng đăng ký nhóm/đề tài. Các tên nhánh và lịch sử commit không tự chứng minh việc đăng ký trên hệ thống môn học.

Quy trình: nhánh thành viên → Pull Request vào `develop` → review và kiểm thử tích hợp → Pull Request từ `develop` vào `main` khi đủ điều kiện nộp. Nội dung báo cáo, kết quả test và bản demo phải khớp cùng phiên bản mã nguồn. Không dùng `git add -f` cho các file local đã bị bỏ qua.

## Tài liệu và dữ liệu

- [Phạm vi đã chốt](docs/PROJECT_SCOPE_2026-10-12.md), [audit validation/UI/backend](docs/VALIDATION_UI_BACKEND_AUDIT_2026-10-12.md) và [kết quả sửa](docs/FIX_VERIFICATION_2026-10-12.md).
- [Hướng dẫn cài đặt](SETUP.md) và [Aiven MySQL](docs/thang/AIVEN_MYSQL.md): cấu hình hiện hành.
- `docs/UTEExpress_PhanCong_QuyTrinh_Teamwork.docx`: **kế hoạch lịch sử**. Các phần SQL Server, `ddl-auto=update`, ghi OTP ra console và lịch triển khai trong tài liệu này đã cũ; cần cập nhật trước khi dùng làm hồ sơ nộp.
- `docs/thang/COMMIT_PLAN.md`: nhật ký/kế hoạch triển khai tại thời điểm viết, không phải trạng thái kiểm thử hiện tại.
- Các script nằm trực tiếp trong `db/` và `db/backup-du-lieu.ps1` là tài nguyên **SQL Server cũ**, không chạy lên Aiven MySQL.
- `db/mysql/` chứa dữ liệu mẫu dành cho MySQL. Chỉ người phụ trách database chạy sau khi thống nhất trong nhóm; thông tin tài khoản demo nằm trong `docs/DEMO_ACCOUNTS.md`.
- `application-cloud.properties.example` là mẫu cấu hình lịch sử, không dùng để chạy phiên bản hiện hành.
