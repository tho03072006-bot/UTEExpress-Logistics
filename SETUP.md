# Khung sườn UTEExpress — hướng dẫn chạy lần đầu

Bộ khung này dựng theo đúng kiến trúc & phân công ở mục 05/03 trong "Kế hoạch UTEExpress".
Mọi TODO trong code là việc thật cần làm tiếp — không phải chỗ nào cũng chạy được ngay.

## 1. Import vào Eclipse
`File → Import → Maven → Existing Maven Projects`, chọn thư mục này. Đợi Eclipse tải
dependency lần đầu (cần mạng, có thể mất vài phút).

## 2. Cấu hình trước khi chạy — KHÔNG sửa trực tiếp application.properties
Copy file `src/main/resources/application-local.properties.example` thành
`application-local.properties` (bỏ đuôi `.example`, cùng thư mục), rồi điền giá trị thật
của máy bạn vào file mới này:
- `spring.datasource.password` — mật khẩu SQL Server thật trên máy bạn (SSMS, `localhost,1433`).
- `spring.mail.username` / `spring.mail.password` — bắt buộc để gửi OTP email thật. Dùng địa chỉ Gmail gửi và App Password của chính hộp thư gửi; không dùng mật khẩu đăng nhập thông thường. Không ghi App Password vào file được Git theo dõi.
- `app.jwt.secret` — chuỗi bất kỳ ≥ 32 ký tự, chỉ cần khi bắt đầu làm JWT.

File `application-local.properties` đã nằm trong `.gitignore` nên sẽ không bao giờ lên Git —
an toàn cho repo public. Không cần đụng vào `application.properties` (file đó chỉ chứa
placeholder `CHANGE_ME`, cứ để nguyên rồi commit bình thường).

Để thử đăng ký/khôi phục mật khẩu với hộp thư nhận `uteexpress8@gmail.com`, nhập địa chỉ đó
trên form của ứng dụng. `spring.mail.username` là hộp thư **gửi**, không nhất thiết là hộp thư
nhận. Nếu SMTP lỗi, ứng dụng không in OTP ra console; hãy kiểm tra cấu hình mail trên máy.

Nhớ tạo sẵn database `uteexpress` trống trong SSMS trên máy bạn — `spring.jpa.hibernate.ddl-auto=update`
sẽ tự tạo bảng khi chạy lần đầu (nếu không tự tạo được, viết script SQL tay như đã làm ở Baitap02).
Mỗi người chạy SQL Server và tạo database riêng trên máy mình — không cần (và không nên) dùng
chung 1 database qua mạng khi code; xem thêm giải thích ở mục "Cơ sở dữ liệu" trong file
"UTEExpress — Phân công & Quy trình Teamwork" (docs/).

## 3. Chạy thử
Trong Eclipse: chuột phải vào `UteexpressApplication.java` → `Run As → Spring Boot App`.
Mở `http://localhost:8080` — phải thấy trang chủ UTEExpress (Bootstrap, có nút "Tạo đơn ngay").

## 4. Trạng thái hiện tại
- Chạy được: trang chủ Guest, trang đăng nhập (giao diện), route rỗng cho từng role.
- Chưa làm: toàn bộ TODO trong code — theo đúng plan ở mục 03 của "Kế hoạch UTEExpress" cho từng thành viên.
- `SecurityConfig` đang mở toàn bộ (permitAll) để cả nhóm build song song không bị chặn đăng nhập —
  PHẢI siết lại theo role khi làm xong chức năng đăng nhập.

## 5. Database chung trên MonsterASP — cách để 3 máy test giống hệt nhau

Vấn đề trước đây: mỗi bạn chạy SQL Server riêng trên máy mình, tài khoản test và dữ liệu
khác nhau, nên demo máy này ra kết quả khác máy kia. Nay nhóm dùng thêm một database
chung đặt trên MonsterASP.

### Dùng profile nào, khi nào

| Profile | Database | Dùng khi |
|---|---|---|
| (mặc định, không bật gì) | SQL Server trên máy mình | Ngồi code hằng ngày — nhanh, sửa hỏng không ảnh hưởng ai |
| `cloud` | MonsterASP, dùng chung cả nhóm | Test tích hợp và **demo với thầy** — 3 máy nhìn cùng một dữ liệu |

### Cài đặt lần đầu (mỗi bạn làm một lần trên máy mình)

1. Copy `src/main/resources/application-cloud.properties.example`
   thành `application-cloud.properties` (bỏ đuôi `.example`, để cùng thư mục).
2. Điền thông tin thật vào các chỗ `CHANGE_ME` — xin trong nhóm chat, **đừng commit**.
   File này đã nằm trong `.gitignore` nên không bao giờ lên Git.
3. Chạy với profile cloud:
   - Dòng lệnh: `mvn spring-boot:run -Dspring-boot.run.profiles=cloud`
   - Eclipse: `Run Configurations` → tab `Arguments` → ô `VM arguments` thêm
     `-Dspring.profiles.active=cloud`

### Lấy thông tin kết nối ở đâu

MonsterASP → `Databases` → chọn database → khung **Access to Database** →
bấm sang tab **Remote access** (KHÔNG phải "Local access").

Hostname ở tab "Local access" chỉ chạy được từ bên trong máy chủ của họ; máy cá nhân
phân giải ra IP nội bộ `10.0.0.x` nên không kết nối tới được.

### Sao lưu dữ liệu — phòng khi mất mạng hoặc sự cố

Gói Free không có bản sao tự động. Trước mỗi buổi demo, chạy:

```powershell
# Sao lưu database chung về máy
.\dbackup-du-lieu.ps1 -Server "HOSTNAME_REMOTE" -Database "TEN_DB" -User "LOGIN" -Password "MAT_KHAU"

# Sao lưu database trên máy mình (không cần tham số)
.\dbackup-du-lieu.ps1
```

File kết quả nằm ở `db/backup/uteexpress_data_<ngày>_<giờ>.sql`, chỉ chứa **dữ liệu**
(câu lệnh tạo bảng đã có sẵn trong `db/01` → `db/06`).

Hôm demo mà mất mạng thì đổ ngược vào máy mình rồi chạy không cần profile cloud:

```powershell
sqlcmd -S "localhost\SQLEXPRESS" -E -C -f 65001 -d uteexpress -i "dbackup\<tên-file>.sql"
```

Thư mục `db/backup/` nằm trong `.gitignore` vì chứa dữ liệu thật của người dùng.

### BẪY: tiếng Việt thành "?" trên database mới

`AppUser.java` và `Order.java` có 6 cột chữ **không có `@Nationalized`**
(`full_name`, `sender_name`, `sender_address`, `receiver_name`, `receiver_address`).
Trên database mới, Hibernate sẽ tạo chúng thành `varchar`, tiếng Việt có dấu lưu xuống
thành dấu `?`. Hai file đó là entity dùng chung, quy ước là không ai tự sửa.

Nên thứ tự dựng database chung **bắt buộc** như sau:

1. Tạm để `spring.jpa.hibernate.ddl-auto=update` trong `application-cloud.properties`
2. Chạy app một lần cho Hibernate tạo xong bảng, rồi tắt đi
3. Chạy lần lượt `db/01` → `db/06` trên database đó (script `01` đổi các cột trên sang `nvarchar`)
4. Đổi lại thành `ddl-auto=validate` và giữ nguyên từ đó

Để `update` lâu dài trên database chung rất nguy hiểm: ai sửa entity là schema đổi cho
cả nhóm mà không ai biết. Với `validate`, app báo lỗi ngay khi entity lệch schema.

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
