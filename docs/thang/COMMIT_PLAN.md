# Chia commit phần Thắng

Các thay đổi được chuẩn bị trên `feature_Thang`. Chưa commit/push/merge. Thực hiện lần lượt các nhóm bên dưới, xem `git diff --cached` trước mỗi commit.

Không dùng `git add .`. Các thay đổi có sẵn trong `.project` và file `tash push -m local config before sync develop -- .project` không thuộc đợt phát triển này và không được đưa vào các commit bên dưới. Cấu hình DB chung trong `application.properties` nay thuộc phạm vi được phép sửa và đã bỏ mật khẩu SQL Server; bản cũ giữ local, không commit. Đặc biệt không commit mật khẩu DB.

```powershell
cd E:\Lap_Trinh_Web\UTEEXPRESS
git branch --show-current
git status --short
git diff --cached --name-only
```

Nhánh phải là `feature_Thang`. Index ban đầu cần trống; nếu có file đang staged từ công việc khác, hãy kiểm tra trước khi tiếp tục. Các nhóm được sắp theo phụ thuộc, không đảo thứ tự. Tất cả nhóm 1–7 được kiểm thử chung ở trạng thái hoàn chỉnh.

## 1. feat: cap tai khoan noi bo khong qua OTP

Hồ sơ staff_account, DTO/validation, cấp tài khoản và quản lý trạng thái/quyền/mật khẩu tại Service. Không sửa entity dùng chung.

- Thêm hồ sơ riêng liên kết 1–1 với `app_user`, trạng thái hoạt động, bắt đổi mật khẩu, phiên bản thu hồi phiên và optimistic revision.
- Admin cấp mới ADMIN/MANAGER bằng BCrypt; không OTP, không nâng quyền USER/SHIPPER đã tồn tại.
- Chuẩn hóa username/email, kiểm tra trùng không phân biệt hoa thường; validation họ tên Unicode và mật khẩu 12–64 ký tự/tối đa 72 byte BCrypt.
- Chặn tự khóa/tự hạ quyền, không làm mất Admin hoạt động cuối cùng, phát hiện form cũ/no-op và rollback giao dịch lỗi.
- Unit test DTO và service cho đường thành công, dữ liệu biên, quyền hạn, khóa đồng thời và rollback.

```powershell
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/entity/admin/StaffAccount.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/repository/admin/AdminUserRepository.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/repository/admin/StaffAccountRepository.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/dto/admin/StaffAccountCreateRequest.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/dto/admin/StaffAccountResponse.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/dto/admin/StaffAccountUpdateRequest.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/dto/admin/StaffPasswordChangeRequest.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/dto/admin/StaffPasswordRequest.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/service/admin/StaffAccountService.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/service/admin/impl/StaffAccountServiceImpl.java'
git add -- 'src/test/java/vn/edu/hcmute/uteexpress/dto/admin/StaffAccountValidationTest.java'
git add -- 'src/test/java/vn/edu/hcmute/uteexpress/service/admin/impl/StaffAccountServiceImplTest.java'
git diff --cached --stat
git diff --cached
git commit -m "feat: cap tai khoan noi bo khong qua OTP"
```

## 2. feat: dung chung database Aiven MySQL

Bỏ driver SQL Server, cấu hình Aiven bắt buộc cho mọi vai trò; TLS xác minh CA/hostname, pool nhỏ, schema validate. Thêm công cụ khởi tạo đủ bảng của cả nhóm trên database trống; chống ghi đè, không seed. Không sửa các file nghiệp vụ của Thọ/Tài. Không đóng gói mật khẩu local vào JAR.

- Toàn app dùng một MySQL Aiven cho User/Shipper/Manager/Admin; bỏ dependency JDBC SQL Server và cấu hình localhost.
- Dùng `VERIFY_IDENTITY`, truststore PKCS12 riêng, tắt fallback truststore/public-key retrieval, đặt timeout và pool tối đa 5 kết nối.
- Chỉ `ddl-auto=validate`; không để app mỗi máy tự ALTER/DROP database chung.
- Sinh DDL MySQL 8 từ toàn bộ 10 entity hiện có, InnoDB/utf8mb4, giữ 12 khóa ngoại và 9 ràng buộc unique.
- Công cụ chỉ khởi tạo khi database hoàn toàn trống, dùng named lock chống chạy đồng thời; không DROP/TRUNCATE/seed.
- Công cụ kiểm tra chỉ đọc kết nối, `SELECT 1`, TLS, phiên bản MySQL và số bảng; test chống URL injection, sai engine, placeholder, ghi đè và lỗi DDL.
- Loại bí mật local/chứng chỉ khỏi Git và JAR; mẫu cấu hình không chứa thông tin thật.

```powershell
git add -- '.gitignore' 'pom.xml'
git add -- 'src/main/resources/application.properties'
git add -- 'src/main/resources/application-local.properties.example'
git add -- 'src/main/resources/database-aiven.properties'
git add -- 'config/aiven-local.properties.example'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/tool/database/AivenConnectionSettings.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/tool/database/CheckAivenConnection.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/tool/database/AivenSchema.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/tool/database/InitializeAivenDatabase.java'
git add -- 'src/test/java/vn/edu/hcmute/uteexpress/tool/database/AivenDatabaseToolsTest.java'
git diff --cached --stat
git diff --cached
git commit -m "feat: dung chung database Aiven MySQL"
```

## 3. feat: them cong cu cap admin dau tien

Công cụ chạy tay trên Aiven MySQL, nhập mật khẩu tài khoản ẩn, BCrypt, khóa chống cấp đồng thời và rollback khi tạo hồ sơ thất bại; không tự seed khi chạy app.

- Chỉ cho tạo Admin đầu tiên khi `staff_account` còn trống; từ chối username/email trùng thay vì đổi quyền tài khoản OTP cũ.
- Ghi `app_user` và `staff_account` trong một transaction, lấy generated key theo chuẩn MySQL và rollback khi bước sau thất bại.
- Dùng named lock để hai người không thể đồng thời tạo hai Admin đầu tiên.
- Mật khẩu tài khoản nhập ẩn và xóa khỏi form sau khi BCrypt; lỗi không in URL/mật khẩu database.

```powershell
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/tool/admin/ProvisionInitialAdmin.java'
git add -- 'src/test/java/vn/edu/hcmute/uteexpress/tool/admin/ProvisionInitialAdminTest.java'
git diff --cached --stat
git diff --cached
git commit -m "feat: them cong cu cap admin dau tien"
```

## 4. feat: bao ve phan quyen admin va manager

Đăng nhập nội bộ, bắt buộc đổi mật khẩu, CSRF, thu hồi phiên, giới hạn đăng nhập sai và chặn OTP chỉ cho cấp quản trị.

- Security chain riêng ưu tiên cao cho `/admin/**`, `/manager/**`, `/noi-bo/**`; Manager bị chặn khỏi toàn bộ Admin route.
- Principal nội bộ chỉ hợp lệ khi có cả `staff_account`, role ADMIN/MANAGER, active/enabled và phiên bản truy cập đúng.
- Phiên cũ bị vô hiệu ngay sau khóa, đổi quyền hoặc đổi mật khẩu; lỗi DB khi kiểm tra quyền trả 503 fail-closed.
- Bắt đổi mật khẩu tạm, xác nhận mật khẩu hiện tại, không tái sử dụng mật khẩu, logout POST và CSRF mặc định.
- Chặn activation/forgot/reset OTP công khai cho ADMIN/MANAGER nhưng giữ nguyên luồng USER/SHIPPER và thông báo chung chống dò email.
- Giới hạn 5 lần đăng nhập sai/15 phút, giới hạn bộ nhớ theo dõi; trang login/lỗi/navigation riêng không hiển thị bí mật.

```powershell
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/config/admin/StaffSecurityConfig.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/config/admin/StaffOtpPolicyConfig.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/security/admin/StaffPrincipal.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/security/admin/StaffSessionFilter.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/security/admin/StaffLoginAttemptService.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/controller/admin/StaffAccessController.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/controller/admin/StaffExceptionHandler.java'
git add -- 'src/main/resources/templates/admin/login.html'
git add -- 'src/main/resources/templates/admin/password-change.html'
git add -- 'src/main/resources/templates/admin/error.html'
git add -- 'src/main/resources/templates/admin/navigation.html'
git add -- 'src/test/java/vn/edu/hcmute/uteexpress/security/admin/StaffLoginAttemptServiceTest.java'
git diff --cached --stat
git diff --cached
git commit -m "feat: bao ve phan quyen admin va manager"
```

## 5. feat: quan ly tai khoan va tach dashboard admin

Giao diện Admin riêng; cấp tài khoản, đổi quyền, khóa/mở, cấp lại mật khẩu; giải thích bảng quyền Admin/Manager.

- Dashboard Admin tách khỏi Manager, hiển thị rõ cấp quyền cao hơn và đường tới quản lý nhân sự nội bộ.
- Danh sách phân trang và form cấp tài khoản; thao tác đổi ADMIN/MANAGER, khóa/mở, reset mật khẩu người khác.
- Dùng revision chống hai tab ghi đè; PRG/thông báo lỗi thân thiện, không phản chiếu password/hash lên HTML.
- Không thêm nút giả cho các chức năng chưa triển khai và không cho Manager thấy chức năng cấp tài khoản.

```powershell
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/controller/admin/AdminController.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/controller/admin/StaffAccountController.java'
git add -- 'src/main/resources/templates/admin/dashboard.html'
git add -- 'src/main/resources/templates/admin/accounts.html'
git add -- 'src/main/resources/templates/admin/account-create.html'
git diff --cached --stat
git diff --cached
git commit -m "feat: quan ly tai khoan va tach dashboard admin"
```

## 6. feat: tra soat don hang cho manager

Danh sách đơn chỉ đọc, bộ lọc chính xác, ngày/trạng thái, phân trang, giao diện tiếng Việt và tổng quan Manager.

- Manager và Admin xem đơn toàn hệ thống nhưng không sửa trạng thái/nghiệp vụ Shipper.
- Lọc chính xác mã vận đơn, enum trạng thái và khoảng ngày bao gồm ngày kết thúc; sắp xếp ổn định theo `createdAt`, `id`.
- Validation mã, ngày tương lai, thứ tự ngày, tối đa 366 ngày và giới hạn page; 20 dòng/trang.
- DTO chỉ trả dữ liệu cần hiển thị, xử lý đơn chưa có Shipper/cước; nhãn trạng thái tiếng Việt.
- Dashboard Manager nêu rõ quyền chỉ tra soát, không có liên kết quản trị tài khoản.

```powershell
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/controller/manager/ManagerOrderController.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/dto/manager/ManagerOrderResponse.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/repository/manager/ManagerOrderReadRepository.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/service/manager/ManagerOrderService.java'
git add -- 'src/main/java/vn/edu/hcmute/uteexpress/service/manager/impl/ManagerOrderServiceImpl.java'
git add -- 'src/main/resources/templates/manager/dashboard.html'
git add -- 'src/main/resources/templates/manager/orders.html'
git add -- 'src/test/java/vn/edu/hcmute/uteexpress/service/manager/impl/ManagerOrderServiceImplTest.java'
git diff --cached --stat
git diff --cached
git commit -m "feat: tra soat don hang cho manager"
```

## 7. test: kiem thu phan quyen va validation noi bo

Kiểm thử tích hợp MVC/security, render Thymeleaf, chống vượt OTP, quyền cấp cao, phiên cũ và kiểm tra JPQL/mapping không kết nối DB.

- Kiểm tra anonymous/USER/SHIPPER/Manager/Admin trên cả GET và POST, redirect đăng nhập, 403 và CSRF.
- Kiểm tra bắt đổi mật khẩu, thu hồi phiên, lỗi database fail-closed và trang không rò password/hash.
- Kiểm tra forged username/email không vượt chặn OTP nội bộ và USER/SHIPPER vẫn dùng OTP bình thường.
- Parse JPQL và mapping bằng MySQL dialect offline để bắt lỗi query trước khi chạy DB thật.

```powershell
git add -- 'src/test/java/vn/edu/hcmute/uteexpress/security/admin/StaffSecurityIntegrationTest.java'
git add -- 'src/test/java/vn/edu/hcmute/uteexpress/repository/admin/StaffQueryValidationTest.java'
git diff --cached --stat
git diff --cached
git commit -m "test: kiem thu phan quyen va validation noi bo"
```

## 8. docs: huong dan test va commit phan thang

Hướng dẫn cấp Admin đầu tiên, phạm vi thay đổi, kiểm thử và chia commit theo từng chức năng.

- Cập nhật SETUP: Aiven MySQL là database duy nhất; phần SQL Server cũ chỉ còn tài liệu lịch sử.
- Hướng dẫn CA/truststore, file local, kiểm tra kết nối, khởi tạo schema, cấp Admin và chạy chung ba máy.
- Ghi rõ ma trận quyền Admin/Manager, validation, giới hạn `uploads`/WebSocket và các bước nghiệm thu.
- Ghi lại kết quả Aiven MySQL 8.4, TLS, đủ 10 bảng và 143 test đạt; không chứa credential.

```powershell
git add -- 'SETUP.md'
git add -- 'docs/thang/AIVEN_MYSQL.md'
git add -- 'docs/thang/ADMIN_MANAGER.md'
git add -- 'docs/thang/COMMIT_PLAN.md'
git diff --cached --stat
git diff --cached
git commit -m "docs: huong dan test va commit phan thang"
```

## Sau khi hoàn tất các commit

```powershell
git status --short
git log -8 --oneline
git push origin feature_Thang
```

Mở Pull Request: base `develop`, compare `feature_Thang`. Mô tả PR nêu rõ thêm hồ sơ `staff_account`, chuyển cấu hình DB chung sang Aiven MySQL mới theo yêu cầu, khởi tạo đủ bảng từ mapping hiện có và không đổi nghiệp vụ/tự nâng quyền khách hàng/shipper. Cần ít nhất một thành viên review/approve trước khi merge; test tích hợp với Aiven MySQL trên `develop` theo quy trình nhóm. Không push trực tiếp lên `develop` hoặc `main`.

## Kết quả kiểm thử ngày 18/09/2026

Java 17; `mvn -B -o "-Dtest=*,!UteexpressApplicationTests" test`: 142 tests, 0 failure, 0 error. Bao gồm 103 test mới và 39 test sẵn có. Các bài test dùng mock; bài kiểm tra JPQL không mở kết nối database. Loại trừ `UteexpressApplicationTests` vì bài đó khởi chạy context với database thật.

Đã chạy thêm `mvn -B "-Dtest=*,!UteexpressApplicationTests" clean package dependency:build-classpath "-Dmdep.outputFile=target/staff-classpath.txt"`: BUILD SUCCESS, 142 tests qua, tạo được JAR. Chưa commit, stage, push hoặc merge.

Đã kết nối Aiven MySQL 8.4 bằng TLS xác minh danh tính, khởi tạo đủ 10 bảng trên database mới trống và chạy `mvn -B test`: 143 tests, 0 failure, 0 error. Bài `UteexpressApplicationTests` khởi động Spring Boot và Hibernate `validate` thành công với Aiven thật. Database chưa có dữ liệu mẫu/tài khoản.

Chưa chạy thử trình duyệt nhiều máy hoặc kiểm thử đồng thời. Chưa commit, stage, push hoặc merge. File `config/aiven-local.properties`, `config/certificates/` và `config/sqlserver-before-aiven.properties` được gitignore, tuyệt đối không ép thêm bằng `git add -f`.
