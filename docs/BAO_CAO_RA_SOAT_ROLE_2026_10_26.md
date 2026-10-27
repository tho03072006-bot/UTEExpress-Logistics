# Báo cáo rà soát vai trò và logic — 26/10/2026

> Cập nhật sau lượt sửa: hai lỗi bên dưới đã được xử lý và bổ sung test hồi quy. Xem [báo cáo sửa lỗi](BAO_CAO_SUA_LOI_2026_10_26.md). Nội dung bên dưới lưu kết quả rà soát trước khi sửa.

## Kết luận

**Còn 2 lỗi điều phối cần sửa, đã tái hiện bằng service thật trên H2. Chưa sửa code nghiệp vụ.** Bộ test hiện có đều đạt, nhưng chưa bao phủ hai tình huống này.

| Kiểm tra | Tổng | Fail | Error | Skipped |
|---|---:|---:|---:|---:|
| Full Maven, kết thúc 23:09 ngày 25/10 | 1.324 / 122 suite | 0 | 0 | 0 |
| Chạy lại 5 nhóm tương tác vai trò ngày 26/10 | 57 | 0 | 0 | 0 |
| JavaScript | 116 | 0 | — | 0 |

Đã kiểm 49 lượt mở trang theo 8 tài khoản trên app local dùng Aiven, cùng các phép kiểm quyền chéo và tính cước bổ sung. Không ghi nhận lỗi máy chủ/template trong lượt kiểm. Đây không phải bằng chứng mọi tổ hợp thao tác web đều đã được nghiệm thu.

## 1. Git và quy mô

- Repo `D:\WEB\Project_CK`, nhánh `feature_Tho_lien_tinh`, HEAD `46e641c`.
- Đã fetch và kiểm lại ngày 26/10: so với `origin/feature_Tho_lien_tinh` là **0 ahead / 0 behind**; không có commit mới cần kéo từ nhánh này.
- Lần fetch cuối phát hiện `origin/develop` đã lên **07a9337**, có **8 commit chưa có trong HEAD** (HEAD so với develop: 0 ahead / 8 behind). Có cập nhật giao diện thanh toán, validation tìm kiếm/phân trang, dashboard và tài khoản. Không tự gộp develop; kết quả bên dưới thuộc bản local HEAD 46e641c cùng các thay đổi chưa commit, chưa bao gồm 8 commit này.
- Ban đầu có **27 file modified, 4 file untracked**. Giữ nguyên các file này; bản đang có thay đổi được dùng để build/test.
- Lượt rà soát chỉ thêm báo cáo này và công cụ/log trong `target/`; không sửa code nghiệp vụ, template, cấu hình hoặc test của dự án.
- Không commit/push, không merge/rebase/stash/reset/checkout--/clean, không mở nội dung file bí mật, không chạy migration `--apply`.

| Thành phần | Số liệu |
|---|---|
| Vai trò | Guest, USER, SHIPPER, MANAGER, ADMIN |
| Chi nhánh | 3: SGN, HAN, DAD |
| Khu vực giao nhận nội bộ | 59: 22 SGN, 30 HAN, 7 DAD |
| Quan hệ lân cận có hướng | 270 |
| Biểu giá tuyến | 21 |
| Tài khoản Aiven | 17: 1 ADMIN, 3 MANAGER, 5 SHIPPER, 8 USER |
| Hoạt động | 16 enabled; 1 USER chưa enabled; 0 bị Admin khóa |
| Nhân sự SGN / HAN / DAD | Lần lượt 1 Manager + 2 Shipper / 1 + 2 / 1 + 1 |

Thống kê tài khoản/danh mục bằng SELECT trong giao dịch chỉ đọc lúc 23:16 ngày 25/10. Không truy vấn mật khẩu hay thông tin liên hệ; không tạo/xóa tài khoản Aiven trong lượt rà soát.

## 2. Hai lỗi cần sửa

### L1 — Giao lại làm vượt giới hạn đơn active (P2)

Tái hiện:

1. Shipper A giữ một đơn FAILED có lịch giao lại đã tới và còn lượt giao.
2. Manager phân công thêm đủ 15 đơn active cho A. FAILED không được tính vào tải active, đúng danh sách trạng thái theo thiết kế.
3. A bắt đầu giao lại đơn FAILED; hệ thống cho đổi sang IN_TRANSIT.
4. Tải active tăng **15 → 16**, trong khi chính sách là 15.

```text
AUDIT_RETRY_CAPACITY before=15 after=16 expected_max=15
```

Nguyên nhân: lúc phân công có kiểm sức chứa; đường giao lại chỉ kiểm lịch, số lần giao và hồ sơ hoàn/duyệt, chưa kiểm việc tái kích hoạt đơn có làm vượt tải hay không.

Vị trí:

- `service/routing/DispatchRoutingPolicy.java:20,95` — danh sách active và kiểm tải khi phân công.
- `service/tracking/impl/TrackingServiceImpl.java:164,173` — điều kiện giao lại và gọi kiểm nghiệp vụ.
- `service/manager/impl/OperationsCaseServiceImpl.java:431` — `beforeStatusChange`, chưa kiểm tải khi FAILED → IN_TRANSIT.

Các đường dẫn trên tương đối với `src/main/java/vn/edu/hcmute/uteexpress/`.

Hướng sửa: kiểm tải ở đường tái kích hoạt đơn, dùng cùng thứ tự khóa với phân công để tránh vượt tải hoặc deadlock khi chạy đồng thời. Thêm regression test tại tải 14/15 và cạnh tranh phân công/giao lại; áp dụng cho cả cập nhật đơn lẻ và hàng loạt.

### L2 — Không đổi được shipper sau khi phân công giao từ kho (P2)

Tái hiện trực tiếp trên DROP_OFF nội tỉnh:

1. Manager nhận đơn tại quầy: trạng thái PICKED_UP, stage DEST_HUB.
2. Phân công A đi giao: stage LAST_MILE, trạng thái vẫn PICKED_UP.
3. A chưa bắt đầu giao nhưng nghỉ ca; Manager muốn chuyển cho B cùng chi nhánh, còn sức chứa.
4. Cả phân công thường và thu hồi đều bị từ chối; đơn không còn ở hàng chờ phân công.

```text
AUDIT_RECLAIM_COUNTER status=PICKED_UP stage=LAST_MILE reclaimable=false pending_contains=false
AUDIT_COUNTER_ASSIGN rejected: Chỉ phân công đơn chờ lấy hoặc chờ giao tại kho nhận.
AUDIT_COUNTER_RECLAIM rejected: Chỉ thu hồi đơn đã lấy hàng mà chưa đi giao, hoặc đơn vừa giao thất bại. Đơn đang trên đường giao thì chờ Shipper báo kết quả.
```

Nguyên nhân: `requireStage()` chỉ cho phân công khi PICKUP/DEST_HUB; lần phân công đầu đã đổi thành LAST_MILE. `isReclaimable()` cho PICKED_UP + PICKUP hoặc FAILED + LAST_MILE, thiếu PICKED_UP + LAST_MILE.

Vị trí: `src/main/java/vn/edu/hcmute/uteexpress/service/routing/DispatchRoutingPolicy.java:63,74`.

Đường chuyển tương tự tồn tại khi phân công giao cho đơn liên tỉnh đã tới kho nhận; phạm vi này đã đối chiếu code. Phép tái hiện trực tiếp dùng DROP_OFF nội tỉnh.

Hướng sửa: cho thu hồi PICKED_UP + LAST_MILE chưa bắt đầu giao; giữ kiểm đúng chi nhánh, sức chứa, dữ liệu chống trang cũ, và không cho can thiệp đơn IN_TRANSIT/đang hoàn. Thêm regression test cho DROP_OFF nội tỉnh và đơn liên tỉnh tại kho nhận.

### Nghi vấn đã loại trừ

Đổi shipper khi đơn đang FAILED **không** tăng số lần giao thất bại. Truy vấn lịch sử đã loại các dòng giữ nguyên trạng thái (`OrderStatusHistoryRepository.java:79`). Đổi shipper hai lần cho một đơn có một lần giao thất bại vẫn cho:

```text
AUDIT_RECLAIM_ATTEMPTS actual_deliveries=1 counted_before=1 counted_after=1 retry_before=true retry_after=true
```

Không ghi nhận điểm này là lỗi. Rà bổ sung phần hủy đơn, trả lượt mã giảm giá, mở yêu cầu hoàn cước, xử lý khiếu nại và hoàn liên tỉnh chưa xác nhận thêm lỗi mới trong phạm vi đã kiểm.

## 3. Kiểm thử tự động

Các lệnh đã chạy:

```powershell
mvn -B -Pmomo-simulator '-Dmaven.test.skip=true' package
mvn -B test
node --test src/test/js/*.test.cjs
mvn -B '-Dtest=CrossRoleSynchronizationIntegrationTest,StaffInterventionScenarioTest,CancellationAndPickupScenarioTest,DropOffIntegrationTest,InterprovincialReturnIntegrationTest' test
```

Lệnh package chuẩn bị simulator theo hướng dẫn dự án; lệnh `mvn test` tiếp theo chạy toàn bộ không loại test. Lệnh cuối chạy lại 5 nhóm liên quan ngày 26/10: 22 + 10 + 4 + 6 + 15 = **57**, đều đạt. Không sửa cấu hình test, không thêm skip hoặc `@Disabled`.

Trong full suite, các nhóm tiêu biểu đều có 0 fail/error/skipped:

| Nhóm | Test |
|---|---:|
| CrossRoleSynchronizationIntegrationTest | 22 |
| InterprovincialReturnIntegrationTest | 15 |
| OperationsControlsIntegrationTest | 47 |
| StaffInterventionScenarioTest | 10 |
| CancellationAndPickupScenarioTest | 4 |
| DropOffIntegrationTest | 6 |
| StaffSecurityIntegrationTest / StaffApiSecurityIntegrationTest | 57 / 10 |
| PublicValidationIntegrationTest / BackendSafetyRegressionTest | 58 / 40 |
| HubConcurrencyIntegrationTest / PaymentConcurrencyIntegrationTest / CodSettlementConcurrencyIntegrationTest | 5 / 2 / 1 |
| MomoSimulatorIntegrationTest | 101 |
| AivenAccountIntegrationTest | 6 |
| AivenOperationsIntegrationTest / AivenControlsIntegrationTest | 2 / 2 |
| AivenMomoConnectionIntegrationTest / AivenMomoPackagedSmokeIntegrationTest | 1 / 1 |

Hai nhóm Aiven từng lỗi trước đây hiện đều đạt. Toàn bộ 122 suite được lưu trong `target/audit-test-summary.csv`; bảng trên chỉ liệt kê một phần.

Phép chẩn đoán L1/L2 chạy riêng bằng `target/RoleLogicProbe.java`, gọi service thật trên H2 in-memory có kiểm URL bắt buộc, rollback và đóng DB. Không cộng vào số 1.324 hoặc 57. Công cụ in kết quả để đối chiếu; exit 0 của công cụ không có nghĩa L1/L2 đúng nghiệp vụ.

## 4. Trình duyệt và tương tác vai trò

App local cổng 8088 dùng Aiven: đăng nhập và đọc trang/bộ lọc/tra cứu/ước tính; không gửi đơn mới, đổi tài khoản, tạo chuyến hoặc ghi hồ sơ trong lượt kiểm này.

| Tài khoản | Lượt mở trang đã lưu | Nội dung |
|---|---:|---|
| manager_sgn | 11 | Đơn, tra cứu hàng loạt, phân công, kho, cảnh báo, hồ sơ, khiếu nại, COD, khuyến mãi, báo cáo, tài khoản |
| manager_han / manager_dad | 4 / 4 | Kho, phân công, hồ sơ, báo cáo đúng chi nhánh |
| admin | 12 | Tài khoản, chi nhánh, chính sách, hai bảng cước, an toàn truy cập, nhật ký, hồ sơ, COD, ba kho |
| thotest | 6 | Tạo đơn, giỏ đơn, địa chỉ, đơn hàng, tài khoản, tra cứu |
| shipper_sgn1 | 6 | Lộ trình, thông báo, COD, thống kê, tài khoản, quét mã |
| shipper_han1 / shipper_dad1 | 3 / 3 | Lộ trình, COD, thống kê |

49 lượt này không tính các màn đăng nhập/dashboard và phép kiểm bổ sung:

- Manager SGN đổi URL sang kho HAN: bị từ chối đúng phạm vi.
- Manager DAD vào trang Admin: bị từ chối.
- Admin lọc ba kho ID 7/8/9: đúng SGN/HAN/DAD.
- Shipper SGN mở đơn ID 310 không được phân công: bị chặn, không hiển thị chi tiết.
- Guest tra `UTE46472965`: thấy giao thành công, tuyến SGN → HAN. Đây là đơn có sẵn, không tạo/sửa trong lượt này.
- Ước tính Thủ Đức → Cầu Giấy, 1 kg, tiêu chuẩn: **43.000 đ** khi shipper đến lấy; **40.000 đ** khi tự mang tới quầy. Form tạo đơn cùng dữ liệu cũng hiện 40.000 đ, giảm đúng 3.000 đ và hiển thị quầy SGN. Không submit tạo đơn.
- Trên LocalDemoApplication/H2 ngày 25/10, shipper xác nhận lấy `UTE-DEMO-01`; người gửi tìm lại thấy `Đã lấy hàng`. Ảnh: `target/audit-customer-after-pickup.png`. Dữ liệu chỉ ở bộ nhớ, không ghi Aiven.

## 5. Schema và việc còn lại

Cả bốn công cụ chỉ chạy `--check`, exit 0:

| Công cụ | Kết quả |
|---|---|
| UpgradeAivenBranches | Thiếu 0/11 bảng; max_active_orders đã có; thiếu 0 chi nhánh/khu vực/lân cận/biểu giá |
| UpgradeAivenDropOff | pickup_mode và drop_off_discount đúng định nghĩa |
| UpgradeAivenReturnLeg | return_leg đúng định nghĩa |
| UpgradeAivenLegacyRoutes | Tự thêm được 0 tuyến; **39 đơn cũ cần xử lý tay** |

39 đơn vẫn theo quy tắc chuyển tiếp, hiện cho mọi chi nhánh. Đây là việc dữ liệu đã biết, không tự chọn tuyến/sửa đơn trong lượt rà soát.

Chưa nghiệm thu thực tế trong lượt này:

- Trọn hành trình hoàn liên tỉnh bằng các nút web trên Aiven. Integration test hoàn liên tỉnh đã đạt 15/15; kết quả đọc trang không thay cho kiểm thao tác ghi.
- Camera vật lý, nhận email thật, Groq thực tế, hoặc thanh toán ví thật. MoMo của dự án là mô phỏng.
- Tải lớn, kiểm thử xâm nhập toàn diện và mọi tổ hợp thao tác đồng thời.
- Phân công riêng shipper kho gửi đi trả hàng hoàn vẫn là tùy chọn; luồng cốt lõi hiện do Manager ghi nhận bàn giao cùng bằng chứng.

Hai lỗi L1/L2 cần sửa và thêm regression test trước khi coi phần điều phối đã hoàn chỉnh. Lượt kiểm không phát hiện thêm lỗi đã xác nhận ngoài hai điểm này; không kết luận hệ thống không còn lỗi.

## 6. Bằng chứng local

Tất cả ở `D:\WEB\Project_CK\target\` (Git bỏ qua):

- `audit-roles-package.log`, `audit-roles-full-test.log`, `audit-test-summary.csv`.
- `audit-role-recheck-2026-10-26.log`: 57 test chạy lại.
- `audit-roles-js-test.log`: 116 test JavaScript.
- `RoleLogicProbe.java`, `audit-role-logic-probe.log`: hai lỗi và nghi vấn đã loại trừ.
- `audit-live-pages.json`: 49 lượt mở trang.
- `audit-account-inventory.log`: đếm tài khoản/danh mục chỉ đọc.
- `audit-branches-check.log`, `audit-UpgradeAivenDropOff-check.log`, `audit-UpgradeAivenReturnLeg-check.log`, `audit-legacy-check.log`.
- `audit-customer-after-pickup.png`: người gửi thấy cập nhật của shipper trên H2.

Không đưa các file target hoặc thông tin xác thực lên Git. Chưa commit hoặc push báo cáo này.
