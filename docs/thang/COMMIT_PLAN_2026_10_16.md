# Commit phần Thắng — bản hiện tại 16/10/2026

Thay thế lệnh trong COMMIT_PLAN_2026_10_10.md. Không chạy cả hai bản. Chưa có commit hoặc push tự động. Các nhóm phụ thuộc shared code/schema của nhau: hoàn thành đủ các nhóm còn thay đổi, kiểm tra toàn bộ rồi mới push. Không coi từng commit trung gian là bản triển khai độc lập.

Không stage README.md, db/backup-du-lieu.ps1, .project, tệp `tash push -m local config before sync develop -- .project`, cấu hình private, CA/truststore, target hay uploads. Dấu `-q` làm output commit gọn hơn, không gộp lịch sử.

Lưu ý lúc kiểm tra để commit: `src/main/resources/application.properties` có thay đổi SMTP cục bộ từ giá trị mẫu sang thông tin đăng nhập. **Không stage file này**; các nhóm dưới cố ý không đưa nó vào. File đã tracked không được `.gitignore` bảo vệ. Muốn đưa thay đổi cấu hình chung lên Git cần tách thông tin SMTP vào cấu hình private trước; không dùng `git add .` hoặc `git commit -a`.

## Chuẩn bị một lần trong cùng PowerShell

```powershell
Set-Location E:\Lap_Trinh_Web\UTEEXPRESS
git branch --show-current
git status --short
git restore --staged -- .
$j = 'src/main/java/vn/edu/hcmute/uteexpress'
$t = 'src/test/java/vn/edu/hcmute/uteexpress'
$v = 'src/main/resources/templates'
```

Nhánh phải là `feature_Thang`; nếu không đúng thì dừng, không chạy các nhóm dưới trên develop. `git restore --staged -- .` chỉ bỏ stage, **không xóa hoặc trả lùi nội dung file**. Nó cần thiết vì trước đó stash/pull đã để lại nhiều file staged lẫn nhau. Không dùng `git restore .`, `git reset --hard`, `git clean`, `git add .` hoặc `git commit -a` cho đợt này.

Nếu bạn đã tự commit một nhóm, bỏ qua nhóm không còn thay đổi; không tạo commit rỗng. Trước mỗi commit có thể dùng `git diff --cached --name-only` để kiểm tra đúng phạm vi.

## 1. Bảo vệ cấu hình và file đóng gói

`.example` chỉ chứa mẫu; giữ mật khẩu ngoài Git, loại cấu hình private/bản sao/khóa/chứng chỉ khỏi JAR.

```powershell
git add -- .gitignore pom.xml config/aiven-local.properties.example src/main/resources/application-local.properties.example docs/thang/SECRETS_AND_EXAMPLES.md
git commit -q -m "chore(security): bao ve cau hinh va bi mat khi dong goi"
```

## 2. Aiven và schema nền tảng

Chỉ Aiven TLS xác minh máy chủ, không fallback local; đồng bộ giờ Việt Nam. Có migration thêm bảng/cột/index an toàn, không xóa dữ liệu. Entity mới được đưa vào nền tảng ở nhóm này, các luồng sử dụng chúng ở những nhóm chức năng bên dưới.

```powershell
git add -- "$j/config/AivenOnlyConfig.java" "$j/config/TimeConfig.java" "$j/tool/database" "$j/entity/admin"
git commit -q -m "feat(db): bo sung schema kiem soat van hanh tren Aiven"
```

## 3. Quyền nội bộ, phiên đăng nhập và nhật ký

Admin/Manager được cấp, không OTP công khai; kiểm tra tài khoản đang hoạt động và thu hồi phiên cũ. Giữ xử lý request nền từ develop, CSRF nội bộ và thông báo lỗi an toàn. Ghi nhật ký quản trị cùng transaction.

```powershell
git add -- @(
  "$j/config/SecurityConfig.java", "$j/exception/GlobalExceptionHandler.java", "$j/exception/StaffConflictException.java",
  "$j/controller/admin/StaffExceptionHandler.java", "$j/controller/admin/AuditController.java", "$j/security",
  "$j/service/impl/AuthServiceImpl.java", "$j/service/admin/StaffAccessPolicy.java",
  "$j/service/admin/AuditService.java", "$j/service/admin/impl/AuditServiceImpl.java",
  "$j/repository/admin/AuditEventRepository.java", "$j/dto/admin/AuditEventResponse.java",
  "$j/dto/admin/StaffAccountResponse.java", "$j/dto/admin/StaffPasswordRequest.java",
  "$j/service/admin/impl/StaffAccountServiceImpl.java", "$j/tool/admin/ProvisionInitialAdmin.java",
  "$v/admin/accounts.html", "$v/admin/audit.html"
)
git commit -q -m "feat(auth): phan quyen noi bo va thu hoi phien dang nhap"
```

## 4. Admin CRUD cả bốn loại tài khoản

Manager không CRUD tài khoản; Admin thêm/sửa/khóa/xóa/reset theo validation. Bảo vệ Admin cuối, bản thân, dữ liệu liên kết và Shipper còn công việc chưa xong.

```powershell
git add -- @(
  "$j/entity/AppUser.java", "$j/controller/admin/AdminAccountController.java", "$j/dto/admin/AdminAccount*",
  "$j/repository/admin/AdminUserRepository.java", "$j/service/admin/AdminAccountService.java",
  "$j/service/admin/impl/AdminAccountServiceImpl.java", "$v/admin/all-*", "$v/admin/account-form-fragments.html",
  "docs/thang/ADMIN_ACCOUNT_CRUD.md"
)
git commit -q -m "feat(admin): CRUD tai khoan bon vai tro va bao ve du lieu"
```

## 5. Cước và lịch giá theo thời điểm

Admin sửa giá/lập lịch, Manager chỉ xem; không trùng khoảng hiệu lực, không hủy lịch đã bắt đầu, không tính lại tiền đơn cũ. Báo giá và tạo đơn dùng chung cước; validation khối lượng và dữ liệu tạo đơn tại service.

```powershell
git add -- @(
  "$j/controller/admin/ShippingTariffController.java", "$j/controller/admin/TariffScheduleController.java", "$j/controller/manager/ManagerTariffController.java",
  "$j/dto/admin/ShippingTariff*", "$j/dto/admin/TariffScheduleRequest.java", "$j/repository/admin/ShippingTariff*",
  "$j/service/admin/ShippingTariffService.java", "$j/service/admin/TariffVersionService.java",
  "$j/service/admin/impl/ShippingTariffServiceImpl.java", "$j/service/admin/impl/TariffVersionServiceImpl.java",
  "$j/entity/Order.java", "$j/dto/OrderCreateRequest.java", "$j/service/impl/OrderServiceImpl.java", "$j/service/FeeEstimateService.java",
  "$v/admin/tariff*", "$v/manager/tariffs.html"
)
git commit -q -m "feat(pricing): quan ly cuoc va phien ban gia theo thoi diem"
```

## 6. Khuyến mãi có giới hạn và lịch sử sử dụng

Giới hạn tiền/thời hạn/lượt; khóa điều kiện tài chính của mã đã dùng; tạo đơn và tăng lượt trong cùng transaction.

```powershell
git add -- @(
  "$j/entity/PromoCode.java", "$j/repository/PromoCodeRepository.java", "$j/service/impl/PromoCodeServiceImpl.java",
  "$j/controller/manager/ManagerPromotionController.java", "$j/dto/manager/Promotion*",
  "$j/service/manager/ManagerPromotionService.java", "$j/service/manager/impl/ManagerPromotionServiceImpl.java", "$v/manager/promotion*"
)
git commit -q -m "feat(manager): quan ly khuyen mai va gioi han su dung"
```

## 7. Điều phối và báo cáo

Giữ phân công/lịch sử/thông báo chung từ develop, thêm audit và chặn Shipper bị khóa. Báo cáo/CSV có giới hạn ngày; FAILED chưa tính là kết quả cuối cùng.

```powershell
git add -- @(
  "$j/service/tracking/DispatchService.java", "$j/repository/manager/Manager*",
  "$j/dto/manager/DispatchOrderResponse.java", "$j/dto/manager/OrderAssignmentRequest.java", "$j/dto/manager/ShipperWorkloadResponse.java", "$j/dto/manager/OperationsReportResponse.java",
  "$j/service/manager/ManagerDispatchService.java", "$j/service/manager/impl/ManagerDispatchServiceImpl.java",
  "$j/controller/manager/ManagerReportController.java", "$j/service/manager/ManagerReportService.java", "$j/service/manager/impl/ManagerReportServiceImpl.java", "$v/manager/report.html"
)
git commit -q -m "feat(manager): dieu phoi an toan va bao cao van hanh"
```

## 8. Chính sách, cảnh báo và hiệu suất Shipper

Admin quản lý ngưỡng; Manager theo dõi việc cần xử lý, khiếu nại/chênh lệch tiền quá hạn và hiệu suất.

```powershell
git add -- @(
  "$j/controller/admin/OperationsPolicyController.java", "$j/dto/admin/OperationsPolicyRequest.java",
  "$j/repository/admin/OperationsPolicyRepository.java", "$j/service/admin/OperationsPolicyService.java", "$j/service/admin/impl/OperationsPolicyServiceImpl.java",
  "$j/dto/manager/ShipperPerformanceResponse.java", "$j/repository/manager/OperationsOverviewRepository.java",
  "$j/service/manager/OperationsOverviewService.java", "$j/service/manager/impl/OperationsOverviewServiceImpl.java",
  "$v/admin/operations-policy.html", "$v/manager/operations-alerts.html", "$v/manager/shipper-performance.html"
)
git commit -q -m "feat(operations): them chinh sach canh bao va hieu suat shipper"
```

## 9. Hồ sơ vận hành, giao lại và hoàn hàng

Đây là aggregate dùng chung cho giao lại/hoàn/tiền: backend chung kiểm tra trạng thái, UUID, revision, maker-checker và chứng từ được commit cùng nhau để không tách rời cơ chế bảo vệ. Nhóm 10–11 ghi riêng tích hợp nghiệp vụ tiền/khiếu nại. Đồng bộ quyền giao lại với danh sách/bộ đếm/lộ trình Shipper; không cho Shipper tự POST RETURNED.

```powershell
git add -- @(
  "$j/controller/manager/OperationsController.java", "$j/dto/manager/OperationsCase*", "$j/dto/manager/OperationsReviewRequest.java",
  "$j/repository/admin/OperationsCaseRepository.java", "$j/service/manager/OperationsCaseService.java", "$j/service/manager/impl/OperationsCaseServiceImpl.java",
  "$j/repository/OrderRepository.java", "$j/repository/OrderStatusHistoryRepository.java", "$j/service/tracking/LogisticsAccess.java",
  "$j/service/tracking/ShipperRouteService.java", "$j/service/tracking/ShipperWorkspaceService.java", "$j/service/tracking/impl/TrackingServiceImpl.java",
  "$v/manager/operations-case.html", "$v/manager/operations-cases.html", "$v/shipper/order-detail.html"
)
git commit -q -m "feat(operations): ho so phe duyet giao lai va hoan hang co chung tu"
```

## 10. Đối soát tiền thiếu/thừa

Giữ phiếu gốc; lập bổ sung/hoàn tiền thừa, giới hạn số còn lại, tiền VND nguyên và Admin độc lập duyệt/ghi biên nhận chi.

```powershell
git add -- @(
  "$j/controller/manager/ManagerCodController.java", "$j/repository/CodSettlementRepository.java", "$j/repository/CodSettlementItemRepository.java",
  "$j/service/tracking/CodSettlementService.java", "$v/logistics/cod-settlement.html"
)
git commit -q -m "feat(reconciliation): xu ly cuoc nop thieu thua khong sua phieu goc"
```

## 11. Khiếu nại, phân công xử lý và bồi hoàn cước

Nhận phụ trách, khóa cập nhật, đối thoại append-only, ảnh chứng từ, đề nghị bồi hoàn cước qua Admin; chặn chuyển trạng thái mâu thuẫn với tiền đã duyệt.

```powershell
git add -- @(
  "$j/controller/tracking/OrderExtrasController.java", "$j/repository/OrderComplaintRepository.java", "$j/repository/admin/ComplaintActivityRepository.java",
  "$j/service/tracking/OrderComplaintService.java", "$v/logistics/complaint.html"
)
git commit -q -m "feat(complaints): lich su xu ly va de nghi boi hoan cuoc"
```

## 12. Kiểm thử, menu tích hợp và hướng dẫn demo

Gộp bộ test hồi quy vì nhiều ca đi xuyên qua nhiều chức năng. H2 chỉ trong test cô lập; kiểm tra Aiven thật rollback dữ liệu QA. Menu phân biệt rõ quyền, tài liệu nêu giới hạn COD cước/chi tiền thủ công.

```powershell
git add -- "$t" ":(exclude)$t/tool/admin/RecoverIssuedStaffAccountsTest.java" "$v/admin/dashboard.html" "$v/admin/navigation.html" "$v/manager/dashboard.html" docs/thang/OPERATIONS_CONTROLS_2026_10_16.md docs/thang/COMMIT_PLAN_2026_10_16.md
git commit -q -m "test: bo sung hoi quy validation va huong dan demo Admin Manager"
```

## 13. Khôi phục tài khoản nội bộ khi quên cả hai mật khẩu

Bổ sung theo yêu cầu sau cùng: CLI quản trị khôi phục đúng tài khoản Admin/Manager đã được cấp, sinh mật khẩu tạm riêng, transaction nguyên tử, BCrypt, thu hồi phiên, bắt buộc đổi mật khẩu, nhật ký và file private chỉ người dùng máy được đọc. CLI đã được chạy cho `admin`/`manager1`; không chạy lại khi commit. Không có endpoint recovery công khai hoặc OTP cho staff. Nhóm này không chứa mật khẩu thật.

```powershell
git add -- .gitignore "$j/tool/admin/RecoverIssuedStaffAccounts.java" "$t/tool/admin/RecoverIssuedStaffAccountsTest.java" docs/thang/STAFF_ACCOUNT_RECOVERY.md
git commit -q -m "feat(auth): khoi phuc tai khoan noi bo va cap lai mat khau an toan"
```

## Kiểm tra và push

```powershell
git status --short
git log -13 --oneline
git diff --check HEAD
mvn -q clean verify
git push origin feature_Thang
```

`mvn` cần Java 17 trở lên và cấu hình Aiven private trên máy; test cloud cần dịch vụ đang hoạt động. Không push khi test thất bại. `git status` vẫn có thể còn README/backup/.project/tệp gõ nhầm ngoài phạm vi: không add chúng để làm status trống.

Nếu `git push` bị non-fast-forward thì dừng, không force-push; cần kiểm tra thay đổi mới của đồng đội. Khi tạo PR để nhóm test, chọn **base develop**, compare **feature_Thang**, không chọn main. Chưa có thao tác merge hoặc push vào develop trong đợt này.
