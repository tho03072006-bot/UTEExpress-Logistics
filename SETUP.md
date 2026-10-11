# Thiết lập UTEExpress — Aiven là database duy nhất

## 1. Java và project

Import Maven project trong Eclipse, chọn JDK 17. Maven ngoài terminal cũng phải dùng Java 17+.

Trên máy Thắng hiện có JDK 17 tại đường dẫn sau:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -v
```

Máy khác thay JAVA_HOME bằng đường dẫn JDK của mình, không sửa HOME/CODEX_HOME.

## 2. File phải điền

- `config/aiven-local.properties`: HOST, PORT, DATABASE, USER, PASSWORD, URL truststore PKCS12 và mật khẩu truststore. Lấy từ dịch vụ MySQL Aiven chung của nhóm, không tạo database riêng trên mỗi máy.
- `config/certificates/`: đặt CA đã tải từ chính dịch vụ Aiven và truststore tạo theo [hướng dẫn TLS](docs/thang/AIVEN_MYSQL.md).
- `src/main/resources/application-local.properties`: SMTP/JWT nếu dùng OTP/JWT; không đặt spring.datasource.* vào đây.

Các file bí mật đã bị Git bỏ qua. File mẫu được commit, file chứa giá trị thật không được commit. File có chữ “local” nghĩa là cấu hình bí mật trên máy, **không** có nghĩa là database local.

Không dùng `application-cloud.properties`, MonsterASP, SQL Server hoặc URL localhost. Không bật `ddl-auto=update/create`. Không đổi sslMode để bỏ qua lỗi chứng chỉ.

## 3. Kiểm tra Aiven / nâng cấp schema

Cả nhóm dừng ứng dụng trước khi thực hiện migration. Đối với database chung đã được nâng cấp ngày 10/10/2026, máy còn lại chỉ pull code, dùng đúng cùng HOST/DATABASE và chạy lại; không cần chạy seed.

```powershell
mvn -q "-DskipTests" compile dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt"
$taskClasspath = "target/classes;" + (Get-Content target/runtime-classpath.txt -Raw).Trim()
java "-Dfile.encoding=UTF-8" -cp $taskClasspath vn.edu.hcmute.uteexpress.tool.database.CheckAivenConnection
```

Nếu dùng database khác đã có schema cũ 10 bảng, chạy công cụ bổ sung cấu trúc:

```powershell
java "-Dfile.encoding=UTF-8" -cp $taskClasspath vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenOperations --apply
java "-Dfile.encoding=UTF-8" -cp $taskClasspath vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenAccounts --apply
```

Công cụ thêm 2 bảng và revision ở Order/PromoCode, tạo 3 mức cước ban đầu nếu thiếu. Có thể chạy lại, không xóa bảng/dữ liệu hoặc ghi đè cước đã sửa. DDL MySQL tự commit: nếu lỗi giữa chừng, kiểm tra cấu trúc trước khi chạy lại. Không tự xóa schema để “chữa” lỗi.

UpgradeAivenAccounts bổ sung revision/locked_by_admin/access_version ở app_user. Aiven chung hiện đã nâng cấp cả hai; không cần mỗi thành viên chạy lại trên cùng database, nhưng cần pull cùng code và restart. Chỉ Admin CRUD cả bốn role tại `/admin/quan-ly-tai-khoan`; xem [hướng dẫn và validation](docs/thang/ADMIN_ACCOUNT_CRUD.md).

Database mới hoàn toàn dùng `InitializeAivenDatabase --initialize-empty` theo [hướng dẫn Aiven](docs/thang/AIVEN_MYSQL.md), không dùng công cụ upgrade trên database trống.

## 4. Chạy và đăng nhập

```powershell
mvn spring-boot:run
```

- Guest/User: `http://localhost:8080`.
- Admin/Manager được cấp: `http://localhost:8080/noi-bo/dang-nhap`.
- Mật khẩu tạm cần đổi ở lần đăng nhập đầu; Admin cấp Manager tại khu tài khoản nội bộ.
- Thông tin đăng nhập đã có thì dùng tài khoản nhóm bàn giao. Công cụ cấp Admin đầu tiên không tạo lại nếu đã có staff.

MySQL/SQL Server và bảng cũ trên máy vẫn được giữ, nhưng không được ứng dụng kết nối. Cấu hình SQL Server/MonsterASP và công cụ backup SQL Server cũ đã gỡ; không sao lưu/xóa database vật lý trong đợt này.

## 5. Xử lý lỗi

- Java sai phiên bản: chỉnh JAVA_HOME/JDK Eclipse.
- TLS/PKIX: kiểm tra đúng CA, truststore, mật khẩu và giờ hệ thống. Không dùng DISABLED/trustServerCertificate để lách lỗi.
- Access denied: kiểm tra DB user/password/quyền trong cấu hình riêng. DB user không phải tài khoản Admin website.
- Timeout: kiểm tra mạng, trạng thái dịch vụ, HOST/PORT và IP allowlist; không chuyển về local.
- Schema validation: kiểm tra cùng database và code mới nhất, không bật update/create.
- Dữ liệu vừa thay đổi: tải lại trang, xem trạng thái mới rồi gửi lại; không bấm lặp mù.
- OTP không gửi được: kiểm tra SMTP. Admin/Manager không dùng OTP đăng ký/khôi phục công khai.

## 6. Test và Git

```powershell
mvn test
```

Xem [kịch bản test chức năng](docs/thang/OPERATIONS_UPGRADE.md) và [lệnh cho từng commit](docs/thang/COMMIT_PLAN_2026_10_10.md). Push feature_Thang, PR base develop; không merge vào main khi đang test nhóm.
