# Khung sườn UTEExpress — hướng dẫn chạy lần đầu

> Cập nhật 20/09/2026: ứng dụng **chỉ dùng MySQL trên Aiven**, một database chung cho User/Shipper/Manager/Admin — không còn SQL Server, không còn MonsterASP và không còn profile `cloud`. Mục 2 (cấu hình) và mục 5 (database chung) bên dưới đã viết lại theo đúng hiện trạng; từng bước chi tiết xem [hướng dẫn Aiven](docs/thang/AIVEN_MYSQL.md). Không chạy các script SQL Server trong `db/` lên Aiven và không bật `ddl-auto=update/create` trên database chung.

Bộ khung này dựng theo đúng kiến trúc & phân công ở mục 05/03 trong "Kế hoạch UTEExpress".
Mọi TODO trong code là việc thật cần làm tiếp — không phải chỗ nào cũng chạy được ngay.

## 1. Import vào Eclipse
`File → Import → Maven → Existing Maven Projects`, chọn thư mục này. Đợi Eclipse tải
dependency lần đầu (cần mạng, có thể mất vài phút).

## 2. Cấu hình trước khi chạy — KHÔNG sửa trực tiếp application.properties

Mỗi máy có **hai** file cấu hình riêng, cả hai đều đã nằm trong `.gitignore` nên không bao giờ
lên Git — an toàn cho repo public:

| File cần tạo | Chứa gì | Copy từ mẫu |
|---|---|---|
| `config/aiven-local.properties` | Kết nối database Aiven (7 mục `AIVEN_*`) | `config/aiven-local.properties.example` |
| `src/main/resources/application-local.properties` | Mail OTP + `app.jwt.secret` | `src/main/resources/application-local.properties.example` |

Không cần đụng vào `application.properties` (file đó chỉ chứa placeholder `CHANGE_ME`, cứ để
nguyên rồi commit bình thường).

### 2.1 Database — `config/aiven-local.properties`

Copy file mẫu (bỏ đuôi `.example`, cùng thư mục) rồi điền đủ 7 mục lấy từ Aiven Console:
`AIVEN_HOST`, `AIVEN_PORT`, `AIVEN_DATABASE`, `AIVEN_USER`, `AIVEN_PASSWORD`,
`AIVEN_TRUSTSTORE_URL`, `AIVEN_TRUSTSTORE_PASSWORD`.

Ba chỗ hay điền sai:
- `AIVEN_HOST` **không** kèm `https://`.
- `AIVEN_PORT` là số thật Aiven cấp, **không** mặc định 3306.
- Đường dẫn Windows trong file `.properties` phải dùng dấu `/`, ví dụ
  `file:/D:/WEB/Project_CK/config/certificates/aiven-truststore.p12`. Đường dẫn có dấu cách
  phải mã hoá thành `%20`.

Kết nối bắt buộc TLS `sslMode=VERIFY_IDENTITY`, nên phải có sẵn CA và truststore: tải CA từ
trang dịch vụ Aiven về `config/certificates/ca.pem`, rồi tạo truststore PKCS12 bằng `keytool`
của JDK (xem lệnh cụ thể ở [hướng dẫn Aiven](docs/thang/AIVEN_MYSQL.md) mục 1). Không tắt SSL
và không bật `allowPublicKeyRetrieval` để né lỗi chứng chỉ.

**Tuyệt đối không đặt `spring.datasource.*` trong `application-local.properties`.** Toàn bộ
datasource do `src/main/resources/database-aiven.properties` lo, và file đó được
`application.properties` nạp sẵn — nên **không cần bật profile nào cả**. Nếu trước đây bạn từng
đặt biến môi trường `SPRING_DATASOURCE_*` hoặc thêm VM argument profile cũ trong Run
Configuration thì phải gỡ, vì các override bên ngoài có ưu tiên cao hơn file.

### 2.2 Mail OTP và JWT — `application-local.properties`

- `spring.mail.username` / `spring.mail.password` — bắt buộc để gửi OTP email thật. Dùng địa chỉ
  Gmail gửi và App Password của chính hộp thư gửi; không dùng mật khẩu đăng nhập thông thường.
  Không ghi App Password vào file được Git theo dõi.
- `app.jwt.secret` — chuỗi bất kỳ ≥ 32 ký tự (dùng cho API tra cứu vận đơn công khai).

Để thử đăng ký/khôi phục mật khẩu với hộp thư nhận `uteexpress8@gmail.com`, nhập địa chỉ đó
trên form của ứng dụng. `spring.mail.username` là hộp thư **gửi**, không nhất thiết là hộp thư
nhận. Nếu SMTP lỗi, ứng dụng không in OTP ra console; hãy kiểm tra cấu hình mail trên máy.

### 2.3 Không phải tạo database trên máy

Không cần cài SQL Server hay MySQL trên máy, cũng không cần tạo database trống. Schema trên Aiven
đã được khởi tạo sẵn một lần cho cả nhóm (xem mục 5). App chạy ở `ddl-auto=validate`, nghĩa là nó
chỉ **kiểm tra** schema và dừng lại báo lỗi nếu entity lệch, chứ không tự `ALTER`/`DROP` database
dùng chung.

## 3. Chạy thử
Trong Eclipse: chuột phải vào `UteexpressApplication.java` → `Run As → Spring Boot App`.
Mở `http://localhost:8080` — phải thấy trang chủ UTEExpress (Bootstrap, có nút "Tạo đơn ngay").

## 4. Trạng thái hiện tại

Cập nhật 20/09/2026 trên nhánh `develop`, 155 test pass.

### Chạy được

- **Guest:** trang chủ (ô tra cứu, 4 thẻ số liệu lấy thật từ database, khối đánh giá khách hàng),
  tra cứu vận đơn, ước tính cước phí, API JSON công khai `/api/tracking/{ma}`.
- **User (người gửi):** đăng ký + OTP email, đăng nhập/đăng xuất, quên mật khẩu + OTP, trang cá
  nhân + đổi mật khẩu + đổi email có OTP, sổ địa chỉ, giỏ đơn chờ xác nhận, tạo đơn, thanh toán
  (COD thật; VNPay/MoMo mô phỏng), mã giảm giá, đánh giá dịch vụ, chi tiết đơn có timeline hành
  trình, in vận đơn.
- **Shipper, Manager, Admin:** đã có khu vực riêng và đã chặn đúng quyền (bảng bên dưới). Chức
  năng nghiệp vụ bên trong do TV2/TV3 phụ trách, xem nhánh `feature_Tai` và `feature_Thang`.

### Phân quyền — đã siết, KHÔNG còn permitAll toàn bộ

Đã thử bằng request thật khi chưa đăng nhập, không khu vực nào để lọt:

| Khu vực | Yêu cầu | Chưa đăng nhập thì |
|---|---|---|
| `/nguoi-dung/**` | đã đăng nhập | 302 → `/dang-nhap` |
| `/shipper/**` | vai trò `SHIPPER` | 302 → `/dang-nhap` |
| `/manager/**` | tài khoản nội bộ | 302 → `/noi-bo/dang-nhap` |
| `/admin/**` | tài khoản nội bộ + `ROLE_ADMIN` | 302 → `/noi-bo/dang-nhap` |

**Bẫy khi đọc code:** trong `SecurityConfig` vẫn còn dòng
`.requestMatchers("/manager/**", "/admin/**").permitAll()`, nhưng **dòng đó không còn tác dụng**.
`StaffSecurityConfig` khai báo `@Order(1)` kèm `securityMatcher("/admin/**", "/manager/**",
"/noi-bo/**")` nên chain của nó được xét trước. Đừng nhìn dòng `permitAll` rồi kết luận khu vực
quản trị đang mở.

### Còn lại 8 chỗ `TODO` trong code

- `SecurityConfig` — **CSRF vẫn đang tắt** cho khu vực công khai/người dùng, cần bật lại cho các
  form HTML. (Chain nội bộ trong `StaffSecurityConfig` vẫn giữ CSRF mặc định.)
- `SecurityConfig` — TODO "TV3 bảo vệ khu vực manager/admin" thực ra **đã xong rồi**, cùng dòng
  `permitAll` chết ở trên; dọn đi được.
- `JwtAuthFilter` — chưa đọc header `Authorization`, JWT chưa dùng tới.
- `OrderServiceImpl` (2 chỗ) — bảng giá cước đang gán cứng, chờ TV3 thay bằng bảng giá theo khu vực.
- `OrderPaymentService` — chờ TV2 gọi tới khi cập nhật trạng thái đơn.
- `ManagerController` — còn route quản lý cần thêm.
- `GlobalExceptionHandler` — tách riêng xử lý lỗi validation.

## 5. Database chung trên Aiven MySQL — cách để 3 máy test giống hệt nhau

Vấn đề trước đây: mỗi bạn chạy SQL Server riêng trên máy mình, tài khoản test và dữ liệu
khác nhau, nên demo máy này ra kết quả khác máy kia. Nay cả nhóm dùng **một database MySQL 8.4
đặt trên Aiven**; User, Shipper, Manager và Admin đọc ghi cùng một nơi.

### Không còn profile nào nữa

Bản cũ có profile `cloud` để chọn giữa database trên máy mình và database chung. Nay chỉ còn
**một** đường: `application.properties` nạp thẳng `classpath:database-aiven.properties`, nên chạy
app chỉ cần

```powershell
mvn spring-boot:run
```

Không thêm `-Dspring-boot.run.profiles=...`, không thêm VM argument profile trong Eclipse.
File `src/main/resources/application-cloud.properties.example` là tàn dư của bản MonsterASP,
ứng dụng không còn đọc tới — bỏ qua nó.

### Cài đặt lần đầu (mỗi bạn làm một lần trên máy mình)

1. Copy `config/aiven-local.properties.example` thành `config/aiven-local.properties`.
2. Điền đủ 7 mục `AIVEN_*` — xin trong nhóm chat, **đừng commit**.
3. Tải CA về `config/certificates/ca.pem` rồi tạo truststore PKCS12 bằng `keytool` của JDK.
4. Chạy `mvn spring-boot:run` từ **thư mục gốc project** (chạy ở chỗ khác sẽ không tìm ra file
   cấu hình local).

Cả ba máy dùng **cùng HOST + PORT + DATABASE**. Từng bước chi tiết, lệnh `keytool` cụ thể và cách
xử lý lỗi TLS/PKIX, `Access denied`, timeout xem [hướng dẫn Aiven](docs/thang/AIVEN_MYSQL.md).

### Khởi tạo schema — ĐÃ XONG, đừng chạy lại

Ngày 18/09/2026 đã tạo xong 10 bảng (InnoDB, utf8mb4) bằng công cụ
`vn.edu.hcmute.uteexpress.tool.database.InitializeAivenDatabase`. Công cụ tự từ chối chạy nếu
database đã có bảng, nên không ai cần và không ai nên chạy lại.

Ba điều cấm trên database dùng chung:

- **Không** bật `ddl-auto=update` / `create` / `create-drop`. Ai sửa entity là schema đổi cho cả
  nhóm mà không ai biết. Để `validate` thì app báo lỗi ngay khi entity lệch schema, thay vì âm
  thầm sửa database chung.
- **Không** chạy các script nằm trực tiếp trong `db/` (`db/01` → `db/06`) lên Aiven — đó là cú
  pháp SQL Server của database cũ, không chạy được trên MySQL.
- Thay đổi schema về sau phải là script được cả nhóm review và chạy **một lần**.

### Dữ liệu mẫu

Chỉ thư mục `db/mysql/` mới dùng được cho Aiven. Script dữ liệu mẫu:

```
db/mysql/01_seed_du_lieu_mau.sql
```

Script **chạy lại được nhiều lần** mà không nhân đôi dữ liệu: nó kiểm tra `NOT EXISTS` theo các
cột unique (`username`, `code`, `tracking_code`) và không gán cứng `id`, nên không đè lên dữ liệu
của người khác.

Nội dung: 11 tài khoản, 80 vận đơn `UTE250001` → `UTE250080` trải đều 3 tháng gần nhất với đủ 7
trạng thái, kèm thanh toán và đánh giá — đủ số liệu thật để nhìn phân trang, bộ lọc, thống kê
Shipper và bảng điều khiển Manager. Mật khẩu mọi tài khoản mẫu là `Test@12345`, riêng `newuser1`
là `MatKhauMoi@2026`.

Cách chạy: mở DBeaver trên kết nối Aiven → `SQL Editor` → mở file → `Execute script`.

### Sao lưu dữ liệu

`db/backup-du-lieu.ps1` viết cho SQL Server, **không dùng được với MySQL** — đừng chạy nhầm.

Máy hiện chưa cài `mysqldump` / `mysql` client, nên cách sao lưu dùng được ngay là DBeaver:
chuột phải vào database → `Tools` → `Dump database`. Muốn dùng `mysqldump` thì phải cài MySQL
client tools trước.

Thư mục `db/backup/` nằm trong `.gitignore` vì chứa dữ liệu thật của người dùng.

### Tiếng Việt có dấu — bẫy cũ đã hết

Bẫy `nvarchar` thời SQL Server (Hibernate sinh cột `varchar`, tiếng Việt có dấu lưu xuống thành
`?`) **không còn áp dụng**: schema trên Aiven tạo bằng `utf8mb4` và chuỗi kết nối đặt
`characterEncoding=UTF-8`, nên cột chữ lưu tiếng Việt bình thường. Thói quen đánh `@Nationalized`
cho cột chữ trong entity mới vẫn giữ được — trên MySQL nó vô hại.

Vẫn giữ nguyên quy ước: `AppUser.java` và `Order.java` là entity dùng chung cả nhóm, không ai
tự sửa, phải hỏi cả nhóm trước.

## 6. Mail OTP — dùng chung một hộp thư

Đăng ký, quên mật khẩu và đổi email đều gửi OTP. Nếu mỗi máy cấu hình Gmail cá nhân
của một bạn thì người nhận thấy tên khác nhau tuỳ máy nào chạy.

Nhóm dùng **một hộp thư chung** (`uteexpress8@gmail.com`). Mỗi bạn điền cùng địa chỉ đó
và cùng App Password vào file cấu hình của máy mình:

```properties
spring.mail.username=uteexpress8@gmail.com
spring.mail.password=<App Password 16 ky tu cua Google>
```

Lưu ý: **phải là "App Password" 16 ký tự** lấy trong Google Account → Bảo mật →
Xác minh 2 bước → Mật khẩu ứng dụng. Mật khẩu đăng nhập Gmail thường sẽ bị từ chối
với lỗi `Authentication failed`.

Tên hiển thị đặt ở `app.mail.from-name` trong `application.properties` (mặc định
`UTEExpress`), nên người nhận luôn thấy:

```
UTEExpress <uteexpress8@gmail.com>
```

Gmail không cho đổi phần địa chỉ — thư vẫn phải gửi từ đúng hộp đã xác thực SMTP,
chỉ tên hiển thị phía trước là tự đặt được.

Chưa cấu hình mail thì đăng ký / quên mật khẩu / đổi email sẽ báo
"Không gửi được email OTP" và dừng lại — đây là hành vi cố ý, không phải lỗi.
