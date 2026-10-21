# UTEExpress — Hệ thống quản lý chuỗi giao nhận Logistics

Đồ án môn Lập trình Web (WEBPR330479) — HCMUTE.
Website cho người gửi hàng tạo đơn, theo dõi hành trình và thanh toán cước; shipper nhận đơn
và cập nhật trạng thái giao hàng.

Các tính năng Shipper bổ sung và luồng dùng thử: [hướng dẫn Shipper](docs/tai/SHIPPER_FEATURES.md).
Project hiện dùng MySQL trên Aiven; xem [SETUP.md](SETUP.md) để cấu hình hiện tại.

## Công nghệ

Spring Boot 3.3.4 · Java 21 · Maven · Thymeleaf + Bootstrap 5.3.3 ·
Spring Data JPA + MySQL 8 (Aiven) · Spring Security 6 · Spring Mail (OTP) · WebSocket · JWT

Kiến trúc 3 tầng: Controller điều hướng → Service xử lý nghiệp vụ → Repository đọc ghi dữ liệu.

## Phân công

| Thành viên | Nhánh | Phụ trách |
|---|---|---|
| Trần Minh Thọ | `feature_Tho` | Guest + User (người gửi hàng) |
| Nguyễn Hữu Tài | `feature_Tai` | Shipper |
| Trần Thắng | `feature_Thang` | Manager + Admin |

Luồng Git: `feature_*` → Pull Request → `develop` → (cuối kỳ) `main`

---

## Chạy lần đầu

### 1. Cần có sẵn trên máy

- JDK 21 và Maven (hoặc Spring Tools for Eclipse đã kèm sẵn)
- Kết nối Internet: dự án dùng **database MySQL chung trên Aiven**, không cần cài database trên máy

### 2. Tạo file cấu hình trong thư mục `config/`

Các file chứa khóa thật đều nằm trong `.gitignore`, **không bao giờ commit**.
Copy từ file mẫu (bỏ đuôi `.example`) rồi điền thông tin xin trong nhóm chat:

| File | Dùng cho |
|---|---|
| `config/aiven-local.properties` + thư mục `config/certificates/` | Kết nối database Aiven (bắt buộc) |
| `config/cloudinary-local.properties` | Lưu ảnh bằng chứng, ảnh đại diện |
| `config/groq-local.properties` | Trợ lý AI |
| `config/jwt-local.properties` | API nội bộ dùng JWT |
| `config/momo-local.properties` | Thanh toán MoMo mô phỏng |

### 3. Chạy app

```powershell
mvn spring-boot:run
```

Trong STS/Eclipse: mở `UteexpressApplication.java` → chuột phải → **Run As → Spring Boot App**.
Lần sau bấm **mũi tên ▾ cạnh nút Run** và chọn `uteexpress - UteexpressApplication`.

> **Đừng chạy `MomoSimulatorApplication`.** Đó là web MoMo giả lập chỉ để deploy lên Render,
> chạy trên máy sẽ báo `Thiếu biến cấu hình AIVEN_HOST`.
>
> Sửa code mà trang không đổi / báo 500 sau khi `git pull`: **Stop** app → chọn project → **F5** →
> **Project → Build Project** → chạy lại. Nếu project hiện là thư mục xám (Closed): **Project → Open Project**.

Mở `http://localhost:8080`. Trang `http://localhost:8080/healthz` trả `ok` khi app đang chạy.

---

### Chạy thử không cần Aiven (dữ liệu mẫu trên máy)

Muốn thử giao diện hoặc thao tác ghi dữ liệu mà không đụng database chung:

1. STS → **Run Configurations** → tạo **Java Application** cho class `vn.edu.hcmute.uteexpress.support.LocalDemoApplication` (nằm trong `src/test/java`).
2. Tab **Environment** → thêm biến `UTE_DEMO_PASSWORD` = mật khẩu bạn tự chọn (ít nhất 6 ký tự) cho các tài khoản mẫu.
3. Run, mở `http://localhost:8085`. Đăng nhập bằng `shipper-demo`, `khach-demo`, `manager-demo`, `admin-demo` với mật khẩu vừa đặt.

Dữ liệu nằm trong bộ nhớ (H2), tắt app là mất; ảnh tải lên lưu tạm, không gửi lên Cloudinary.
Nếu app chính đang chạy cổng 8085 thì thêm biến `UTE_DEMO_PORT` (ví dụ `8095`).

---

## Database chung (Aiven)

- App chạy với `ddl-auto=validate`: sửa entity mà chưa có bảng/cột tương ứng thì app **báo lỗi khởi động**.
  Cần đổi schema thì **báo nhóm**, viết công cụ nâng cấp trong `tool/database` (kiểu `UpgradeAivenProfiles`,
  có `--check` / `--apply`) rồi mới chạy lên database chung.
- Database dùng chung cho cả nhóm: **không tạo dữ liệu thử lung tung**; muốn thử ghi dữ liệu thì viết test chạy trên H2.
- Không commit file trong `config/`. Repo public, lộ khóa là người lạ sửa/xóa được dữ liệu.

---

## Mail OTP

Đăng ký, quên mật khẩu và đổi email đều gửi mã OTP từ hộp thư chung của nhóm.
Điền `spring.mail.username` và `spring.mail.password` (App Password 16 ký tự của Gmail) vào file cấu hình trên máy.
Chưa cấu hình mail thì các chức năng này báo "Không gửi được email OTP" — đây là hành vi cố ý.

---

## Tài khoản test

**Mật khẩu xin trong nhóm chat — không ghi vào README hay commit lên Git.**
Tài khoản nội bộ (Manager/Admin) đăng nhập ở `/noi-bo/dang-nhap` và **bị khóa sau 5 lần nhập sai**;
mọi trang đăng nhập còn tạm chặn theo IP nếu sai quá 20 lần trong 10 phút.

| Tên đăng nhập | Vai trò | Ghi chú |
|---|---|---|
| `thotest`, `huongshop`, `minhstore`, `lanboutique` | USER | Có sẵn đơn hàng để demo |
| `newuser1` | USER | |
| `chuakichhoat` | USER | Chưa kích hoạt OTP, dùng thử ca đăng nhập lỗi |
| `shipper1`, `shipper2`, `shipper3` | SHIPPER | |
| `manager1` | MANAGER | Đăng nhập ở `/noi-bo/dang-nhap` |
| `admin` | ADMIN | Đăng nhập ở `/noi-bo/dang-nhap` |

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
- **Giao diện**: lớp chung `static/css/ute-app.css` (kiểu template quản trị, menu trái cho mọi vai trò) nạp sau cùng,
  ghi đè `style.css`. Màu chính xanh `#0072BC`; màu phụ chỉ dùng cho trạng thái và ô số liệu.
  Trước khi đặt tên lớp CSS mới, tìm trong các file CSS/JS xem đã có chưa để tránh trùng.
  Có sẵn hai chế độ sáng/tối.

### Hai entity dùng chung — không ai tự sửa

`Order.java` và `AppUser.java` là của cả nhóm. Cần lưu thêm dữ liệu thì **tạo entity riêng**
trỏ tới chúng bằng `@ManyToOne` / `@OneToOne`, không thêm cột vào hai file đó.
Các entity `SavedAddress`, `DraftOrder`, `OrderPayment`, `ServiceReview`, `PromoCode`,
`DeliveryProof`, `EmailChangeRequest` đều làm theo cách này.

### Chữ tiếng Việt trong cột mới

Database chung dùng `utf8mb4`. Vẫn giữ thói quen đánh `@Nationalized` cho mọi cột chữ trong entity mới
để chạy đúng cả trên H2 lúc test.

---

## Chạy test

```powershell
mvn test "-Dtest=!Aiven*IntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Test chạy trên H2 riêng trong bộ nhớ, không đụng database chung. Các test `Aiven*IntegrationTest` cần kết nối Aiven thật nên bỏ qua khi chạy thường.
`InputFuzzIntegrationTest` tự gửi dữ liệu xấu vào mọi form để bảo đảm không trang nào bị lỗi 500.

### CI trên GitHub

Mỗi pull request vào `develop`/`main` và mỗi lần push lên `develop`, GitHub Actions
(`.github/workflows/test.yml`) tự build và chạy toàn bộ test Java 21 bằng H2 trong bộ nhớ
(bỏ qua `Aiven*IntegrationTest` và `UteexpressApplicationTests` vì cần database thật),
cùng các test JS trong `src/test/js` bằng Node 20. CI không có file bí mật trong `config/`
và không kết nối Aiven. PR có dấu ✗ đỏ nghĩa là build hoặc test hỏng — sửa xong mới merge.

## Tài liệu chi tiết

`SETUP.md` — hướng dẫn cài đặt đầy đủ hơn, kèm cách xử lý các lỗi thường gặp.
