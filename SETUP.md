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
