# Aiven MySQL dùng chung cho cả nhóm

## Quyết định và trạng thái

Theo yêu cầu mới nhất của Thắng: dùng **database Aiven mới**, không chuyển dữ liệu cũ. Toàn ứng dụng chỉ dùng MySQL Aiven, không còn driver/kết nối SQL Server mặc định. Ngày 18/09/2026 đã kết nối thành công MySQL 8.4 bằng TLS xác minh danh tính và đã khởi tạo schema trống gồm 10 bảng; chưa tạo dữ liệu mẫu hoặc tài khoản thật. Không xóa database cũ; bản cấu hình trước khi đổi nằm ở `config/sqlserver-before-aiven.properties`, được gitignore và không được ứng dụng đọc.

User, Shipper, Manager và Admin dùng cùng Spring DataSource nên đọc/ghi cùng database. Không sửa entity/controller/service/repository nghiệp vụ của Thọ hoặc Tài. Bộ khởi tạo đọc mapping của cả nhóm để tạo đủ 10 bảng, 12 khóa ngoại, 9 ràng buộc unique; dùng InnoDB và utf8mb4. Không seed tài khoản hay nâng quyền tài khoản OTP.

## 1. Chuẩn bị dịch vụ và cấu hình local

1. Trong Aiven Console, chọn dịch vụ **MySQL 8 trở lên** đang chạy. Chọn một database **trống hoàn toàn**, có thể dùng `defaultdb` nếu chưa có bảng. Nếu chưa có dịch vụ, người sở hữu tài khoản tạo theo gói phù hợp; mã nguồn không tự tạo dịch vụ và không đăng ký gói trả phí.
2. Lấy HOST, PORT, DATABASE, USER và tải CA certificate ở trang thông tin dịch vụ. Hướng dẫn chính thức: [Aiven kết nối MySQL](https://aiven.io/docs/products/mysql/howto/connect-from-mysql-workbench).
3. Cả ba máy dùng **cùng HOST + PORT + DATABASE**. Tốt nhất mỗi thành viên có DB user riêng với quyền cần thiết trên đúng database đó. Mật khẩu DB không phải mật khẩu đăng nhập Admin/Manager trong ứng dụng.
4. Từ thư mục gốc project, sao chép mẫu nếu chưa có file local (không ghi đè file đã điền):

```powershell
cd E:\Lap_Trinh_Web\UTEEXPRESS
if (!(Test-Path 'config/aiven-local.properties')) {
    Copy-Item 'config/aiven-local.properties.example' 'config/aiven-local.properties'
}
New-Item -ItemType Directory -Path 'config/certificates' -Force
```

Đặt CA vừa tải vào `config/certificates/ca.pem`. Với Java 17, tạo truststore riêng; nhập mật khẩu khi keytool hỏi, không đặt mật khẩu trong câu lệnh:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
& "$env:JAVA_HOME\bin\keytool.exe" -importcert -alias aiven-ca -file 'config/certificates/ca.pem' -keystore 'config/certificates/aiven-truststore.p12' -storetype PKCS12
```

Kiểm tra chứng chỉ đúng với dịch vụ trước khi chấp nhận. Nếu alias đã tồn tại, không xóa/ghi đè vội: kiểm tra có phải cùng CA không. TLS dùng `VERIFY_IDENTITY`: kiểm tra cả CA và hostname; không tắt SSL, không bật `allowPublicKeyRetrieval`. Dùng truststore riêng theo [hướng dẫn Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html).

Điền đủ 7 mục `AIVEN_*` trong `config/aiven-local.properties`: host, port, database, user, password, URL truststore, mật khẩu truststore. HOST không gồm `https://`, PORT dùng số thật từ Aiven, không mặc định 3306. Đường dẫn Windows dùng `/`, ví dụ `file:/E:/Lap_Trinh_Web/UTEEXPRESS/config/certificates/aiven-truststore.p12`; đường dẫn có dấu cách phải mã hóa thành `%20`. Biến môi trường cùng tên có thể thay cấu hình file. Công cụ chỉ chấp nhận hostname thuộc `aivencloud.com`, không nhận JDBC URL tùy ý.

Mail/JWT vẫn dùng file local cũ; chỉ bỏ các mục `spring.datasource.*` cũ trong file local của từng người. Cấu hình Aiven được nạp sau file local, không cần bật profile. Không tự sửa file local của người khác. Loại bỏ cấu hình datasource/profile cũ trong Run Configuration hoặc biến môi trường `SPRING_DATASOURCE_*` nếu đã tự đặt trước đây: các override bên ngoài có ưu tiên cao hơn file.

Không commit file local, mật khẩu, CA/truststore hoặc bản sao cấu hình cũ. Maven không đóng gói `application-local.properties` vào JAR. Chạy từ thư mục gốc project để tìm đúng các file local.

## 2. Khởi tạo một lần cho cả nhóm

Chỉ một người thực hiện, sau khi cả nhóm dừng app. Tài khoản DB của người khởi tạo cần quyền CREATE/ALTER/REFERENCES trên database đích. Các máy chạy app bình thường chỉ cần quyền CRUD thích hợp, không cần quyền DDL.

```powershell
mvn -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/staff-classpath.txt"
$aivenClasspath = 'target/classes;' + (Get-Content -Raw 'target/staff-classpath.txt').Trim()
# Kiểm tra kết nối/TLS/schema chỉ đọc:
& "$env:JAVA_HOME\bin\java.exe" -cp $aivenClasspath vn.edu.hcmute.uteexpress.tool.database.CheckAivenConnection
# Chỉ xem DDL offline, không kết nối:
& "$env:JAVA_HOME\bin\java.exe" -cp $aivenClasspath vn.edu.hcmute.uteexpress.tool.database.InitializeAivenDatabase --print-schema
# Tạo bảng thật; công cụ hỏi xác nhận trong terminal:
& "$env:JAVA_HOME\bin\java.exe" -cp $aivenClasspath vn.edu.hcmute.uteexpress.tool.database.InitializeAivenDatabase --initialize-empty
```

Công cụ kiểm tra MySQL >= 8, đúng database, lấy khóa chống hai lần chạy đồng thời và từ chối nếu đã có bất kỳ bảng/view nào (kể cả bảng rỗng). Không tạo/xóa database, không DROP/TRUNCATE và không thay schema có sẵn. MySQL DDL tự commit: nếu mất mạng/lỗi quyền giữa chừng, có thể còn một phần bảng; không tuyên bố rollback được DDL. Phải kiểm tra và chọn database mới trống hoặc nhờ người quản trị xử lý có review, không chạy lại mù quáng.

Không dùng các script SQL Server cũ trong `db/` trên Aiven. Schema phát sinh từ đúng phiên bản entity đang checkout; cả nhóm phải đồng bộ code trước khi khởi tạo. Những thay đổi schema sau này cần script được review và chạy một lần, không bật `ddl-auto=update/create/create-drop`. App hiện chỉ `validate`: sai schema thì dừng thay vì âm thầm sửa database chung.

## 3. Cấp Admin và chạy ứng dụng

Sau khi tạo bảng, một người chạy:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -cp $aivenClasspath vn.edu.hcmute.uteexpress.tool.admin.ProvisionInitialAdmin
```

Công cụ dùng cùng cấu hình Aiven, hỏi username/họ tên/email/mật khẩu tạm (ẩn), lưu BCrypt, không OTP. Chỉ tạo Admin mới khi bảng `staff_account` chưa có hồ sơ. Có khóa chống cấp đồng thời và transaction cho cả hai bảng; không ghi đè/nâng quyền người dùng cũ. Đăng nhập `/noi-bo/dang-nhap`, đổi mật khẩu tạm; Admin cấp Manager khác qua giao diện.

Mỗi thành viên sau khi đã điền cấu hình riêng:

```powershell
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Ho_Chi_Minh"
```

Trong Eclipse chọn Java 17, working directory là project, VM argument `-Duser.timezone=Asia/Ho_Chi_Minh`. Không bật profile/config SQL Server cũ. Pool tối đa 5 kết nối mỗi app; tổng số máy/app phải nằm trong hạn mức kết nối của dịch vụ.

## 4. Nghiệm thu đồng bộ

- Máy Thọ tạo USER qua OTP và tạo đơn; máy Thắng tải lại trang Manager thấy đúng đơn đó.
- Theo nghiệp vụ hiện có của Tài, Shipper cập nhật trạng thái; các máy khác tải lại và thấy trạng thái mới. Kiểm tra phân trang, đếm đơn, giờ Việt Nam, Unicode, đơn chưa có Shipper.
- Admin cấp Manager; Manager không vào được `/admin/**`; thử khóa/đổi quyền và kiểm tra phiên bị thu hồi trên yêu cầu tiếp theo.
- Kiểm tra đồng thời hai tab với revision cũ, trùng username/email và mạng gián đoạn. Kiểm thử offline không thay thế nghiệm thu với MySQL thật.
- Không dùng dữ liệu khách hàng thật để demo; tránh sửa/xóa dữ liệu test của thành viên khác. Thống nhất tên dữ liệu test, thời gian reset và backup trước thao tác quản trị.

**Giới hạn đồng bộ:** MySQL chỉ đồng bộ dữ liệu bảng. `uploads/` chứa ảnh/chữ ký vẫn ở máy/server chạy app; đường dẫn trong DB không làm tệp xuất hiện trên máy khác. WebSocket và phiên đăng nhập/rate limit trong bộ nhớ cũng không tự chia sẻ giữa các app. Bản này đảm bảo cùng nguồn dữ liệu khi truy vấn/tải lại, không khẳng định push realtime xuyên máy. Để dùng trọn luồng tệp/realtime cùng nhau mà chưa đổi code của Thọ/Tài, cả nhóm có thể truy cập một app server chung; triển khai server/storage chung là bước riêng, chưa thực hiện.

## 5. Xử lý lỗi

- Thiếu `AIVEN_*`/`CHANGE_ME`: điền file local, chạy đúng thư mục; không thay bằng database localhost.
- TLS/PKIX/hostname mismatch: kiểm tra CA, truststore, mật khẩu và hostname đầy đủ. Không dùng `sslMode=DISABLED` để bỏ lỗi.
- Access denied: kiểm tra DB user/password/quyền trên đúng database; không dùng tài khoản web Admin thay cho DB user.
- Timeout: kiểm tra dịch vụ đang chạy, DNS/port/firewall/IP allowlist của nhóm; không tự mở truy cập toàn Internet. Không đổi tài khoản hoặc khởi tạo lại schema để chữa mất mạng.
- Table missing/schema-validation: kiểm tra đúng database và phiên bản code; chỉ người phụ trách DB xử lý sau review.
- Duplicate/deadlock: kiểm tra thông báo nghiệp vụ và tải lại trạng thái trước khi thử lại; không lặp mù thao tác cấp tài khoản.

Đã xác nhận kết nối, TLS, đủ 10 bảng và Spring Boot `ddl-auto=validate` trên Aiven thật. Toàn bộ 143 test đạt; chưa chạy thử trình duyệt nhiều máy, chưa tạo Admin thật và chưa kiểm thử đồng thời. Xem `COMMIT_PLAN.md` để commit theo từng nhóm thay đổi.
