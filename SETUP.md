# Khung sườn UTEExpress — hướng dẫn chạy lần đầu

Bộ khung này dựng theo đúng kiến trúc & phân công ở mục 05/03 trong "Kế hoạch UTEExpress".
Mọi TODO trong code là việc thật cần làm tiếp — không phải chỗ nào cũng chạy được ngay.

## 1. Import vào Eclipse
`File → Import → Maven → Existing Maven Projects`, chọn thư mục này. Đợi Eclipse tải
dependency lần đầu (cần mạng, có thể mất vài phút).

## 2. Cấu hình trước khi chạy
Mở `src/main/resources/application.properties`, thay các giá trị `CHANGE_ME`:
- `spring.datasource.password` — mật khẩu SQL Server thật trên máy bạn (SSMS, `localhost,1433`).
- `spring.mail.username` / `spring.mail.password` — chỉ cần khi bắt đầu làm OTP email, có thể để tạm.
- `app.jwt.secret` — chuỗi bất kỳ ≥ 32 ký tự, chỉ cần khi bắt đầu làm JWT.

Nhớ tạo sẵn database `uteexpress` trống trong SSMS — `spring.jpa.hibernate.ddl-auto=update`
sẽ tự tạo bảng khi chạy lần đầu (nếu không tự tạo được, viết script SQL tay như đã làm ở Baitap02).

## 3. Chạy thử
Trong Eclipse: chuột phải vào `UteexpressApplication.java` → `Run As → Spring Boot App`.
Mở `http://localhost:8080` — phải thấy trang chủ UTEExpress (Bootstrap, có nút "Tạo đơn ngay").

## 4. Trạng thái hiện tại
- Chạy được: trang chủ Guest, trang đăng nhập (giao diện), route rỗng cho từng role.
- Chưa làm: toàn bộ TODO trong code — theo đúng plan ở mục 03 của "Kế hoạch UTEExpress" cho từng thành viên.
- `SecurityConfig` đang mở toàn bộ (permitAll) để cả nhóm build song song không bị chặn đăng nhập —
  PHẢI siết lại theo role khi làm xong chức năng đăng nhập.
