# Lệnh commit phần Thắng — nâng cấp 10/10/2026

Chạy lần lượt trong **cùng một cửa sổ PowerShell**, tại E:\Lap_Trinh_Web\UTEEXPRESS. Mỗi nhóm dùng `git add` cho đúng file rồi `git commit -q`; không có hàm/helper hoặc commit tự động. `-q` giảm phần hiển thị dài, không gộp commit. Các nhóm phụ thuộc nhau; commit đủ 12 nhóm rồi mới push/test chung. Tài liệu COMMIT_PLAN.md cũ là lịch sử, không chạy lại lệnh cũ. Nhóm 10–12 bổ sung theo yêu cầu mới: chỉ Admin CRUD cả Admin/Manager/User/Shipper. Nhóm 1 riêng cho bảo vệ mật khẩu và file `.example`.

Workspace hiện chưa commit các thay đổi của đợt này, nên dùng bản 12 nhóm dưới. Nếu bạn đã tự chạy một phần bản cũ trước đó, không chạy lại toàn bộ; dùng git status để chỉ stage các file còn thay đổi tương ứng và bỏ qua commit không còn diff. Không hứa tách một file đã commit thành các commit cũ khác nhau.

## Chuẩn bị

```powershell
Set-Location E:\Lap_Trinh_Web\UTEEXPRESS
git branch --show-current
git status --short
git diff --cached --name-only
$j = "src/main/java/vn/edu/hcmute/uteexpress"
$t = "src/test/java/vn/edu/hcmute/uteexpress"
$v = "src/main/resources/templates"
```

Nhánh hiện tại phải là feature_Thang; nếu khác, dừng và chuyển đúng nhánh sau khi xử lý thay đổi đang có. Nếu đã có file staged từ trước, kiểm tra và bỏ stage riêng chúng trước khi chạy các nhóm dưới, không gộp thay đổi của người khác.

Không dùng `git add .` hoặc `git commit -a`. Không đưa .project, file “tash push -m local config before sync develop -- .project”, cấu hình mật khẩu, CA/truststore hoặc target vào commit. Hai thay đổi ngoài phạm vi từ trước vẫn được giữ nguyên. Không có commit/push tự động trong đợt triển khai này.

File cấu hình SQL Server private đã gỡ không được theo dõi trên Git, nên không cần git add cho file đó; database và bảng local vẫn còn nguyên.

## Commit 1: chore(security): bao ve cau hinh va mat khau

File `.example` chỉ chứa giá trị mẫu; giữ nguyên các file đã điền mật khẩu. Bỏ qua cấu hình private, bản sao/backup, `.env` và khóa/truststore. `.gitignore` không bảo vệ file đã tracked hoặc xóa mật khẩu trong lịch sử.

```powershell
git add -- .gitignore config/aiven-local.properties.example src/main/resources/application-local.properties.example docs/thang/SECRETS_AND_EXAMPLES.md
git commit -q -m "chore(security): bao ve cau hinh va mat khau"
```

## Commit 2: chore(db): chi dung Aiven cloud

Bỏ cấu hình/công cụ SQL Server/MonsterASP cũ; chặn local/profile ghi đè, giữ database vật lý; thống nhất múi giờ Việt Nam.

```powershell
git add -- @(
    "README.md",
    "SETUP.md",
    "db/backup-du-lieu.ps1",
    "src/main/resources/application-cloud.properties.example",
    "src/main/resources/application.properties",
    "src/main/resources/database-aiven.properties",
    "$j/config/AivenOnlyConfig.java",
    "$j/config/TimeConfig.java",
    "$j/tool/database/AivenConnectionSettings.java",
    "$t/config/AivenOnlyConfigTest.java"
)
git commit -q -m "chore(db): chi dung Aiven cloud"
```

## Commit 3: feat(db): them schema van hanh Aiven

Thêm bảng cước/nhật ký, revision của Order/PromoCode/AppUser, locked_by_admin và access_version của tài khoản; migration lặp lại an toàn, công cụ cấp Admin đầu tiên tương thích và kiểm tra schema 12 bảng.

```powershell
git add -- @(
    "$j/entity/Order.java",
    "$j/entity/PromoCode.java",
    "$j/entity/AppUser.java",
    "$j/entity/admin/ShippingTariff.java",
    "$j/entity/admin/AuditEvent.java",
    "$j/repository/admin/ShippingTariffRepository.java",
    "$j/repository/admin/AuditEventRepository.java",
    "$j/tool/database/AivenSchema.java",
    "$j/tool/database/CheckAivenConnection.java",
    "$j/tool/database/InitializeAivenDatabase.java",
    "$j/tool/database/UpgradeAivenOperations.java",
    "$j/tool/database/UpgradeAivenAccounts.java",
    "$j/tool/admin/ProvisionInitialAdmin.java",
    "$t/tool/database/AivenDatabaseToolsTest.java"
)
git commit -q -m "feat(db): them schema van hanh Aiven"
```

## Commit 4: feat(admin): ghi nhat ky thao tac noi bo

Admin xem nhật ký; thao tác tài khoản và các chức năng mới ghi cùng transaction, không lưu mật khẩu; helper quyền dùng chung tại Service.

```powershell
git add -- @(
    "$j/dto/admin/AuditEventResponse.java",
    "$j/service/admin/AuditService.java",
    "$j/service/admin/impl/AuditServiceImpl.java",
    "$j/service/admin/StaffAccessPolicy.java",
    "$j/exception/StaffConflictException.java",
    "$j/controller/admin/AuditController.java",
    "$j/service/admin/impl/StaffAccountServiceImpl.java",
    "$v/admin/audit.html",
    "$t/service/admin/impl/StaffAccountServiceImplTest.java"
)
git commit -q -m "feat(admin): ghi nhat ky thao tac noi bo"
```

## Commit 5: feat(admin): cau hinh cuoc dung chung

Admin sửa cước, Manager chỉ xem; báo giá/tạo đơn User dùng cước Aiven, tiền nguyên và trọng lượng hợp lệ; tạo/hủy đơn trong transaction.

```powershell
git add -- @(
    "$j/dto/admin/ShippingTariffResponse.java",
    "$j/dto/admin/ShippingTariffUpdateRequest.java",
    "$j/service/admin/ShippingTariffService.java",
    "$j/service/admin/impl/ShippingTariffServiceImpl.java",
    "$j/controller/admin/ShippingTariffController.java",
    "$j/controller/manager/ManagerTariffController.java",
    "$j/service/impl/OrderServiceImpl.java",
    "$j/dto/OrderCreateRequest.java",
    "$v/admin/tariffs.html",
    "$v/admin/tariff-edit.html",
    "$v/manager/tariffs.html",
    "$t/service/impl/OrderFeeEstimateTest.java",
    "$t/service/admin/impl/ShippingTariffServiceImplTest.java"
)
git commit -q -m "feat(admin): cau hinh cuoc dung chung"
```

## Commit 6: feat(manager): dieu phoi shipper cho don cho lay

Phân công/đổi shipper đang hoạt động, hiển thị tải đơn, chặn trạng thái không phù hợp và form cũ, không đổi nghiệp vụ giao/bằng chứng.

```powershell
git add -- @(
    "$j/dto/manager/DispatchOrderResponse.java",
    "$j/dto/manager/ShipperWorkloadResponse.java",
    "$j/dto/manager/OrderAssignmentRequest.java",
    "$j/repository/manager/ManagerDispatchRepository.java",
    "$j/repository/manager/ManagerShipperRepository.java",
    "$j/service/manager/ManagerDispatchService.java",
    "$j/service/manager/impl/ManagerDispatchServiceImpl.java",
    "$j/controller/manager/ManagerDispatchController.java",
    "$v/manager/dispatch.html"
)
git commit -q -m "feat(manager): dieu phoi shipper cho don cho lay"
```

## Commit 7: feat(manager): quan ly khuyen mai co validation

Tạo/sửa/bật/tắt mã; kiểm tra %/tiền/hạn/lượt; giữ điều kiện mã đã dùng; khóa mã lúc tạo đơn để bảo vệ lượt cuối.

```powershell
git add -- @(
    "$j/dto/manager/PromotionRequest.java",
    "$j/dto/manager/PromotionResponse.java",
    "$j/dto/manager/PromotionStatusRequest.java",
    "$j/service/manager/ManagerPromotionService.java",
    "$j/service/manager/impl/ManagerPromotionServiceImpl.java",
    "$j/controller/manager/ManagerPromotionController.java",
    "$j/repository/PromoCodeRepository.java",
    "$j/service/impl/PromoCodeServiceImpl.java",
    "$v/manager/promotions.html",
    "$v/manager/promotion-edit.html"
)
git commit -q -m "feat(manager): quan ly khuyen mai co validation"
```

## Commit 8: feat(manager): bao cao van hanh va CSV

Số đơn theo trạng thái, tỷ lệ giao, cước thực trả theo ngày thanh toán; CSV tổng hợp không dữ liệu khách hàng.

```powershell
git add -- @(
    "$j/dto/manager/OperationsReportResponse.java",
    "$j/service/manager/ManagerReportService.java",
    "$j/service/manager/impl/ManagerReportServiceImpl.java",
    "$j/controller/manager/ManagerReportController.java",
    "$v/manager/report.html"
)
git commit -q -m "feat(manager): bao cao van hanh va CSV"
```

## Commit 9: test(staff): kiem tra quyen validation va Aiven

Menu/ma trận quyền rõ ràng, xử lý lỗi an toàn, regression và test Aiven rollback, tài liệu demo và từng commit.

```powershell
git add -- @(
    "$j/controller/admin/StaffExceptionHandler.java",
    "$j/exception/GlobalExceptionHandler.java",
    "$v/admin/navigation.html",
    "$v/admin/dashboard.html",
    "$v/manager/dashboard.html",
    "$t/service/manager/AivenOperationsIntegrationTest.java",
    "$t/service/manager/impl/ManagerOperationsServiceImplTest.java",
    "$t/dto/manager/OperationsValidationTest.java",
    "docs/thang/AIVEN_MYSQL.md",
    "docs/thang/ADMIN_MANAGER.md",
    "docs/thang/OPERATIONS_UPGRADE.md",
    "docs/thang/COMMIT_PLAN_2026_10_10.md"
)
git commit -q -m "test(staff): kiem tra quyen validation va Aiven"
```

## Commit 10: fix(auth): khoa va thu hoi phien ca bon vai tro

User/Shipper kiểm tra tài khoản trên mỗi request; khóa/xóa/đổi quyền/mật khẩu làm hết phiên. Mở khóa không hồi sinh phiên cũ. OTP không mở tài khoản Admin đã khóa; Admin/Manager chỉ đăng nhập và khôi phục nội bộ. Chain nội bộ cũ vẫn giữ CSRF.

```powershell
git add -- @(
    "$j/config/SecurityConfig.java",
    "$j/security/UserDetailsServiceImpl.java",
    "$j/security/admin/StaffPrincipal.java",
    "$j/security/admin/PublicAccountPrincipal.java",
    "$j/security/admin/PublicAccountSessionFilter.java",
    "$j/service/impl/AuthServiceImpl.java",
    "$t/security/UserDetailsServiceImplTest.java",
    "$t/security/admin/PublicAccountSessionFilterTest.java",
    "$t/service/impl/AuthServiceImplTest.java"
)
git commit -q -m "fix(auth): khoa va thu hoi phien ca bon vai tro"
```

## Commit 11: feat(admin): CRUD tai khoan ca bon vai tro

Trang quản trị riêng chỉ Admin: thêm, sửa thông tin/quyền/trạng thái, reset mật khẩu, xóa có xác nhận; username bất biến, bảo vệ Admin cuối cùng và dữ liệu nghiệp vụ. Validate ở DTO + Service + DB, chống form cũ; rate limit mật khẩu xác nhận; không lưu password/OTP vào nhật ký. Manager không có CRUD tài khoản nào.

```powershell
git add -- @(
    "$j/controller/admin/AdminAccountController.java",
    "$j/service/admin/AdminAccountService.java",
    "$j/service/admin/impl/AdminAccountServiceImpl.java",
    "$j/repository/admin/AdminUserRepository.java",
    "$j/dto/admin/AdminAccountProfileRequest.java",
    "$j/dto/admin/AdminAccountCreateRequest.java",
    "$j/dto/admin/AdminAccountActionRequest.java",
    "$j/dto/admin/AdminAccountUpdateRequest.java",
    "$j/dto/admin/AdminAccountDeleteRequest.java",
    "$j/dto/admin/AdminAccountPasswordRequest.java",
    "$j/dto/admin/AdminAccountResponse.java",
    "$j/dto/admin/StaffAccountResponse.java",
    "$j/dto/admin/StaffPasswordRequest.java",
    "$v/admin/all-accounts.html",
    "$v/admin/all-account-create.html",
    "$v/admin/all-account-edit.html",
    "$v/admin/all-account-delete.html",
    "$v/admin/all-account-password.html",
    "$v/admin/account-form-fragments.html",
    "$v/admin/accounts.html"
)
git commit -q -m "feat(admin): CRUD tai khoan ca bon vai tro"
```

## Commit 12: test(admin): kiem thu CRUD phan quyen va migration

Validation dữ liệu, GET/POST/CSRF và role, bỏ mật khẩu khỏi form lỗi; CRUD cả bốn role trên Aiven thật với tài khoản QA rollback. Kiểm tra không xóa tài khoản có dữ liệu, không ghi đè form cũ, migration chỉ thêm cột và chạy lại không thay dữ liệu. Tài liệu demo và giới hạn thực tế.

```powershell
git add -- @(
    "$t/security/admin/StaffSecurityIntegrationTest.java",
    "$t/service/admin/impl/AdminAccountServiceImplTest.java",
    "$t/service/admin/AivenAccountIntegrationTest.java",
    "$t/dto/admin/AdminAccountValidationTest.java",
    "$t/tool/database/UpgradeAivenAccountsTest.java",
    "docs/thang/ADMIN_ACCOUNT_CRUD.md"
)
git commit -q -m "test(admin): kiem thu CRUD phan quyen va migration"
```

## Kiểm tra và push

```powershell
mvn test
git status --short
git --no-pager log --oneline -12
git push origin feature_Thang
```

Maven phải dùng JDK 17+ và có cấu hình Aiven/TLS đúng; xem SETUP.md. Build sạch đạt 297 tests, 0 failure/error/skipped; chi tiết trong ADMIN_ACCOUNT_CRUD.md. Test tích hợp dùng Aiven thật và rollback dữ liệu kiểm thử. Chưa thay thế kiểm tra hai trình duyệt/nhiều máy, SMTP thật hoặc stress test.

Nếu Git mở màn hình dài/pager, bấm q để thoát. Các lệnh commit ở trên dùng `-q`; có thể xem lại bằng `git --no-pager log --oneline -12`. Chỉ chạy commit tiếp theo khi `git add` và `git commit` trước đó thành công; nếu báo `nothing to commit`, kiểm tra nhóm đó đã commit chưa, không dùng `--allow-empty`.

PR: **base develop**, compare **feature_Thang**. Không chọn main cho giai đoạn test nhóm. Tiêu đề gợi ý: `Nâng cấp Manager/Admin, CRUD tài khoản và đồng bộ Aiven`. Nội dung: 5 nhóm chức năng vận hành + CRUD cả 4 role chỉ Admin, validation/quyền/phiên, shared code/schema 12 bảng và 3 cột bảo vệ tài khoản, không xóa local DB, kết quả test và mục kiểm tra thủ công còn lại.

Aiven chung đã được nâng cấp bổ sung; không cần chạy migration lại trên mỗi máy nếu cùng HOST/DATABASE. Các bạn pull code sau merge develop, kiểm tra cấu hình chung và restart. Không chạy seed hoặc code cũ song song rồi kỳ vọng cước/version đồng nhất.

