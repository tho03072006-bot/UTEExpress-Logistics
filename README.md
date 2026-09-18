# UTEExpress — Hệ thống quản lý chuỗi giao nhận Logistics

Đồ án môn Lập trình Web (WEBPR330479) — HCMUTE.
Website cho người gửi hàng tạo đơn, theo dõi hành trình và thanh toán cước; shipper nhận đơn
và cập nhật trạng thái giao hàng.

## Công nghệ

Spring Boot 3.3.4 · Java 17 · Maven · Thymeleaf + Bootstrap 5.3.3 ·
Spring Data JPA + SQL Server · Spring Security 6 · Spring Mail (OTP) · WebSocket · JWT

Kiến trúc 3 tầng: Controller điều hướng → Service xử lý nghiệp vụ → Repository đọc ghi dữ liệu.

## Phân công

| Thành viên | Nhánh | Phụ trách |
|---|---|---|
| Trần Minh Thọ | `feature_Tho` | Guest + User (người gửi hàng) |
| Nguyễn Hữu Tài | `feature_Tai` | Shipper |
| Trần Thắng | `feature_Thang` | Manager + Admin |

Luồng Git: `feature_*` → Pull Request → `develop` → (cuối kỳ) `main`

---

## Chạy lần đầu — 4 bước

### 1. Cần có sẵn trên máy

- JDK 17 trở lên (dự án đang chạy JDK 21)
- Maven
- SQL Server (bản Express là đủ) — chỉ cần nếu muốn chạy bằng database trên máy mình

> **Lưu ý:** nếu `mvn` báo lỗi `class file has wrong version 61.0, should be 52.0`
> nghĩa là `JAVA_HOME` đang trỏ vào Java 8. Trỏ lại vào JDK 17+ rồi chạy lại.

### 2. Tạo file cấu hình

Dự án có 2 file cấu hình chứa mật khẩu thật, **cả hai đều nằm trong `.gitignore`** nên
không bao giờ lên Git. Mỗi người tự tạo trên máy mình từ file mẫu:

| Tạo file này | Từ file mẫu | Dùng để |
|---|---|---|
| `src/main/resources/application-local.properties` | `application-local.properties.example` | Chạy bằng SQL Server trên máy mình |
| `src/main/resources/application-cloud.properties` | `application-cloud.properties.example` | Chạy bằng database chung của nhóm |

Mở file mẫu ra, copy thành file thật (bỏ đuôi `.example`) rồi điền vào các chỗ `CHANGE_ME`.
**Thông tin thật xin trong nhóm chat, đừng commit lên Git.**

### 3. Tạo bảng trong database (chỉ khi chạy bằng database trên máy mình)

Tạo database rỗng tên `uteexpress` trong SSMS, chạy app một lần cho Hibernate tạo bảng,
rồi chạy lần lượt các script trong thư mục `db/`:

```powershell
sqlcmd -S "localhost\SQLEXPRESS" -E -C -f 65001 -i "db\01_chuyen_cot_chu_sang_nvarchar.sql"
# ... làm tương tự cho 02, 03, 04, 05, 06 theo đúng thứ tự
```

> **Quan trọng — nếu bỏ qua bước này thì tiếng Việt có dấu sẽ lưu thành `?`.**
> Script `01` đổi các cột chữ sang `nvarchar`. Cờ `-f 65001` bắt buộc phải có,
> thiếu là dữ liệu tiếng Việt trong script hỏng thật chứ không phải hiển thị sai.

### 4. Chạy app

```powershell
# Dùng database trên máy mình — code hằng ngày thì dùng cái này cho nhanh
mvn spring-boot:run

# Dùng database chung của nhóm — khi test chung và khi demo
mvn spring-boot:run -Dspring-boot.run.profiles=cloud
```

Trong Eclipse: `Run Configurations` → tab `Arguments` → ô `VM arguments`
thêm `-Dspring.profiles.active=cloud` nếu muốn chạy database chung.

Mở `http://localhost:8080`.

---

## Database chung của nhóm

Trước đây mỗi người chạy SQL Server riêng nên tài khoản test và dữ liệu khác nhau,
demo máy này ra kết quả khác máy kia. Nay nhóm có thêm một database chung đặt trên
MonsterASP để ba máy nhìn cùng một dữ liệu.

**Vẫn giữ database trên máy mình** để code hằng ngày — nhanh hơn, và sửa hỏng cũng
không ảnh hưởng người khác.

### Ba điều phải nhớ

1. **Đừng commit `application-cloud.properties`.** Repo này public, lộ mật khẩu là
   người lạ xoá được dữ liệu.

2. **Database chung để `ddl-auto=validate`, không phải `update`.** Sửa entity
   (thêm/đổi cột) thì app sẽ **báo lỗi khởi động** chứ không tự đổi schema — đây là cố ý.
   Cần đổi schema thì **báo nhóm rồi viết thêm script `db/07_...sql`** và chạy lên
   database chung, giống cách nhóm vẫn làm từ đầu.

3. **Chỉ bật `cloud` khi cần.** Ba người cùng nghịch một database dễ phá dữ liệu của nhau.

### Sao lưu — phòng khi mất mạng hoặc sự cố

Gói đang dùng không có sao lưu tự động. Trước buổi demo nên kéo dữ liệu về máy:

```powershell
# Sao lưu database chung (thông tin kết nối xin trong nhóm chat)
.\db\backup-du-lieu.ps1 -Server "<hostname>" -Database "<ten-db>" -User "<login>" -Password "<mat-khau>"

# Sao lưu database trên máy mình
.\db\backup-du-lieu.ps1
```

File kết quả nằm ở `db/backup/`, chỉ chứa **dữ liệu** (câu lệnh tạo bảng đã có sẵn
trong `db/01` → `db/06`). Hôm demo mất mạng thì đổ ngược vào máy rồi chạy không cần
profile cloud:

```powershell
sqlcmd -S "localhost\SQLEXPRESS" -E -C -f 65001 -d uteexpress -i "db\backup\<ten-file>.sql"
```

---

## Mail OTP

Đăng ký, quên mật khẩu và đổi email đều gửi mã OTP. Nhóm dùng **chung một hộp thư**
để người nhận luôn thấy cùng một tên người gửi:

```
UTEExpress <uteexpress8@gmail.com>
```

Điền `spring.mail.username` và `spring.mail.password` vào file cấu hình của máy mình.
**Mật khẩu phải là "App Password" 16 ký tự** lấy trong Google Account → Bảo mật →
Xác minh 2 bước → Mật khẩu ứng dụng. Mật khẩu đăng nhập Gmail thường sẽ bị từ chối
với lỗi `Authentication failed`.

Chưa cấu hình mail thì ba chức năng trên sẽ báo "Không gửi được email OTP" và dừng lại —
đây là hành vi cố ý, không phải lỗi.

---

## Tài khoản test

Mật khẩu đều là `Test@12345`, riêng `newuser1` là `MatKhauMoi@2026`.

| Tên đăng nhập | Vai trò | Ghi chú |
|---|---|---|
| `thotest` | USER | Có sẵn đơn hàng, địa chỉ, đánh giá — dùng để demo |
| `newuser1` | USER | Mật khẩu khác |
| `chuakichhoat` | USER | Chưa kích hoạt OTP, dùng để thử ca đăng nhập lỗi |
| `shipper1` | SHIPPER | |
| `manager1` | MANAGER | |
| `admin` | ADMIN | |

Mã giảm giá: `UTE10` (giảm 10%), `FREESHIP15` (giảm 15.000đ cho đơn từ 30.000đ),
`HETHAN` (đã hết hạn), `DATAT` (đã tắt) — hai mã sau dùng để thử ca lỗi.

---

## Quy ước code của nhóm

- **Tên class, method, biến**: tiếng Anh chuẩn Java.
  **Comment và mọi nội dung hiển thị cho người dùng**: tiếng Việt có dấu đầy đủ.
- Bảng và cột đặt `snake_case`, khoá chính luôn là `id`, enum luôn `@Enumerated(EnumType.STRING)`,
  tiền tệ dùng `BigDecimal`, thời gian dùng `created_at` / `updated_at` kiểu `LocalDateTime`.
- Route người dùng: tiếng Việt không dấu, kebab-case, tiền tố `/nguoi-dung/...`.
  Route API: tiếng Anh, tiền tố `/api`.
- **Mọi trang Thymeleaf phải đi qua template chung** `fragments/base.html`,
  không tự dựng lại head/navbar/footer.
- **Bảng màu chỉ xanh `#0072BC` + trắng + xám trung tính** (biến `--ute-*` trong
  `static/css/style.css`). Không dùng đỏ/vàng/lục ở bất kỳ đâu, kể cả để báo trạng thái.
  Giao diện có sẵn hai chế độ sáng/tối, thêm trang mới không phải làm gì thêm.

### Hai entity dùng chung — không ai tự sửa

`Order.java` và `AppUser.java` là của cả nhóm. Cần lưu thêm dữ liệu thì **tạo entity riêng**
trỏ tới chúng bằng `@ManyToOne` / `@OneToOne`, không thêm cột vào hai file đó.
Các entity `SavedAddress`, `DraftOrder`, `OrderPayment`, `ServiceReview`, `PromoCode`,
`DeliveryProof`, `EmailChangeRequest` đều làm theo cách này.

### Bẫy `nvarchar`

Hibernate mặc định sinh cột `varchar` trên SQL Server, tiếng Việt có dấu lưu xuống
thành `?`. **Mọi cột chữ trong entity mới phải đánh `@Nationalized`.**

---

## Chạy test

```powershell
mvn test
```

## Tài liệu chi tiết

`SETUP.md` — hướng dẫn cài đặt đầy đủ hơn, kèm cách xử lý các lỗi thường gặp.
