# BÀN GIAO: LÀM TIẾP PHẦN CÒN LẠI CỦA KẾ HOẠCH LIÊN TỈNH 3 CHI NHÁNH

Bạn tiếp quản code trên nhánh **`feature_Tho_lien_tinh`** (GitHub `tho03072006-bot/UTEExpress-Logistics`, commit `18db294`),
tách ra từ `develop` tại `4ce5661`. Trên máy tôi repo nằm ở `D:\WEB\Project_CK` (Windows).
Đọc trước hai file: `docs/PROMPT_CHATGPT_LIEN_TINH.md` (các quyết định đã chốt B1–B11, bảng bước C, kịch bản nghiệm thu)
và `docs/KE_HOACH_LIEN_TINH.md`. File này chỉ ghi **trạng thái hiện tại** và **việc còn lại**. Chỗ nào hai file mâu thuẫn thì theo file này.

---

## 1. Quy tắc bắt buộc (giữ nguyên như trước)

1. **Làm trên nhánh `feature_Tho_lien_tinh`.** Không tạo nhánh khác, không commit, không push, không `git stash/reset/checkout --/clean/rebase/merge`, không force push. Tôi kiểm xong và nói rõ "commit" thì mới commit.
2. **Không ghi gì lên Aiven** (DB dùng chung cả nhóm) khi tôi chưa xác nhận rõ. Được chạy `--print-schema` và `--check` (chỉ đọc). `--apply` thì dừng lại hỏi tôi.
3. **Không đọc, in hay sửa** `config/*-local.properties`, `src/main/resources/application-local.properties`, `config/certificates/*`.
4. **Không sửa** `entity/Order.java`, `entity/AppUser.java`, giá trị enum `OrderStatus`. Thêm dữ liệu thì tạo bảng/entity riêng.
5. Không `@Disabled`, không skip test, không đổi cấu hình test để cho xanh. Báo số liệu thật.
6. Giao diện, comment, thông báo, email: **tiếng Việt có dấu**. Bảng màu chỉ xanh `#0072BC`, trắng, xám. Lỗi dùng `.ute-field-error`.
7. Kiến trúc 3 tầng: controller không gọi repository; mọi kiểm tra quyền ở server.
8. Không ghi tên công cụ AI vào code, comment, commit.
9. Tài khoản demo trên DB chung: chỉ đăng nhập để kiểm, không đổi mật khẩu.

Lệnh build (PowerShell):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -q -B test-compile
mvn -q -B test "-Dtest=TenTest1,TenTest2" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -q -B -Pmomo-simulator "-Dmaven.test.skip=true" package   # cần trước khi chạy full test (test smoke MoMo)
mvn -q -B test
```

Với `-q`, test đạt thì không in gì. Muốn chắc, đọc số liệu trong `target/surefire-reports/TEST-*.xml`.

Chạy công cụ migration (chỉ `--check`):

```powershell
mvn -q -B compile dependency:build-classpath "-Dmdep.outputFile=target/branches-classpath.txt"
$cp = 'target/classes;' + (Get-Content target/branches-classpath.txt -Raw).Trim()
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $cp vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenDropOff --check
```

---

## 2. Trạng thái hiện tại (tối 23/10/2026)

Bước 1–11 trong bảng C **đã có code và test đạt**, nằm trong commit `18db294` (195 file). Bước 12 làm được một phần. Bước 13–17 và kiểm tra cuối **chưa làm**.

### 2.0. Việc phải hỏi tôi TRƯỚC KHI code tiếp: `develop` vừa có cách tính cước thứ hai

Sau khi tách nhánh, Tài push lên `develop` 2 commit (`c097b85` "phụ phí khoảng cách đường chim bay giữa hai địa chỉ", `f2b4877` test JS).
Commit này **thêm cột vào `Order.java`**, lấy tọa độ địa chỉ qua ArcGIS (`service/geo/*`, `OrderPricingService`), có migration `UpgradeAivenOrderDistance` / `db/migration/V7__order_distance.sql`,
và **trùng 8 file** với nhánh này: `UserOrderController`, `FeeEstimateService`, `OrderDraftServiceImpl`, `OrderServiceImpl`, `shipper/order-detail.html`, `user/order-detail.html`, `user/order-form.html`, `OrderFeeEstimateTest`.
Nhánh này đã tính phụ phí khoảng cách theo tâm khu vực (B7, `RouteQuoteService` + `RouteDistance`), nên hai cách tính sẽ chồng nhau.
**Không tự merge `origin/develop`.** Xem `git diff 4ce5661 origin/develop`, rồi trình tôi phương án (giữ cách nào, gộp ra sao, cột nào trong `Order.java` còn dùng, migration nào đã apply lên Aiven) và chờ tôi chọn.

### 2.0b. File cấu hình không có trong Git

`config/*-local.properties` (Aiven, JWT, Cloudinary, Groq, MoMo) và `config/certificates/*` bị gitignore. Máy mới thì tôi gửi riêng; bạn chỉ đặt đúng chỗ, không mở ra in, không commit.

### 2.1. CẢNH BÁO: DB chưa có 2 cột mới, web chưa chạy được với Aiven

Entity đã có `OrderRoute.pickupMode` và `OperationsPolicy.dropOffDiscount`, nhưng migration `tool/database/UpgradeAivenDropOff` **chưa được apply** lên Aiven.
App dùng `ddl-auto=validate` nên **sẽ không khởi động** cho tới khi tôi apply. Việc của bạn: chạy `--check` (sẽ báo 2 cột "còn thiếu"), rồi **dừng hỏi tôi**.
Có thể gom chung lần hỏi với công cụ chuyển dữ liệu ở bước 16.

Hai câu migration (đã có `--print-schema`, khóa `GET_LOCK`, timeout từng câu, chạy lại không lỗi, test `UpgradeAivenDropOffTest`):

```sql
ALTER TABLE order_route ADD COLUMN pickup_mode ENUM('COURIER','DROP_OFF') NOT NULL DEFAULT 'COURIER';
ALTER TABLE operations_policy ADD COLUMN drop_off_discount DECIMAL(12,0) NOT NULL DEFAULT 3000;
```

Lưu ý thêm: trên máy tôi, một tiến trình app cũ (Spring Boot, cổng 8080, chạy code cũ) có thể vẫn đang chạy. Trước khi kiểm web, kiểm tra cổng 8080 và hỏi tôi trước khi tắt.

### 2.2. Những gì đã làm (để bạn dùng lại, đừng viết trùng)

**Bước 7–9 (phân công, chặng shipper, kho):**
- `service/tracking/DispatchService`, `service/routing/DispatchRoutingPolicy`: phân công theo B5. `scope(actor, requested)` trả `null` = Admin xem mọi chi nhánh, `-1` = Manager chưa gán chi nhánh, ném `AccessDeniedException` khi Manager đòi chi nhánh khác.
- `entity/routing/OrderRoute`: các hàm chuyển chặng `assignShipper`, `startLastMile`, `receiveAtOriginHub`, `receiveAtCounter`, `departLinehaul`, `arriveDestHub`, `backToOriginHub`, `choosePickupMode`.
- `TrackingServiceImpl.handoverToHub` + nút "Bàn giao vào kho" ở `templates/shipper/order-detail.html`. Đơn liên tỉnh ở chặng lấy không được tự chuyển `IN_TRANSIT`.
- `service/routing/HubService` + `controller/manager/HubController` + `templates/manager/hub.html` (`/manager/kho`): tạo/hủy chuyến, niêm phong xuất kho, nhận chuyến đối chiếu niêm phong và từng kiện, kiện thiếu ghi chú và nhận bổ sung.

**Bước 9b (Tự mang ra chi nhánh, B11):** xong toàn bộ trừ apply DB.
- `RouteQuoteService.quote(..., PickupMode)`: giảm `drop_off_discount`, không vượt tổng (tổng không âm), mã giảm giá áp sau. `Quote` có thêm `dropOffDiscount`, `pickupMode`.
- `OrderCreateRequest.pickupMode`, `FeeEstimateRequest.pickupMode`; radio ở `user/order-form.html` và `uoc-tinh-cuoc.html`; `static/js/route-quote.js` gửi `pickupMode`, hiện địa chỉ quầy, khóa nút "Thêm vào giỏ" khi chọn tự mang.
- Giỏ đơn **không nhận** DROP_OFF (`OrderDraftServiceImpl.addDraft` báo lỗi rõ). `OrderSubmissionGuard.fingerprint` chỉ thêm "DROP_OFF" khi tự mang để mã băm đơn cũ không đổi.
- Hàng chờ phân công (`OrderRouteRepository.DISPATCHABLE`) và cảnh báo "Chưa phân công" bỏ đơn DROP_OFF. Cảnh báo "Chậm lấy" ghi "Khách chưa mang hàng tới chi nhánh".
- `HubService.receiveAtCounter` + mục "Chờ khách mang hàng tới quầy" trên trang kho. Nội tỉnh → `PICKED_UP/DEST_HUB`, liên tỉnh → `PICKED_UP/ORIGIN_HUB`. Ghi lịch sử, nhật ký `COUNTER_RECEIVE`, email `RECEIVED_AT_COUNTER`.
- Admin sửa mức giảm (0–20.000 đ) ở `/admin/chinh-sach-van-hanh`, nhật ký ghi mức cũ → mới.
- Test: `UpgradeAivenDropOffTest` (7), `RouteQuoteServiceTest` (25), `DropOffIntegrationTest` (5, H2).

**Bước 10 (Manager chỉ thấy dữ liệu chi nhánh mình), dùng chung các mảnh sau:**
- `OrderRouteRepository.BRANCH_SCOPE`: điều kiện JPQL cho đơn bí danh `o`, tham số `:branchId`.
- `StaffBranchRepository.USER_SCOPE`: điều kiện JPQL cho nhân sự bí danh `u` (Shipper của chi nhánh).
- `service/routing/BranchScope`: `of(actor, requested)`, `branches(actor)`, `visible(actor, orders)`, `requireOrder(actor, order)`, `requireShipper(actor, shipper)`, `isAdmin(actor)`.
- `fragments/branch-filter.html`: `select(branches, branchId)` (ô chọn chỉ hiện với Admin) và `note(branches)`.
- `LogisticsAccess.checkOrder`: Manager mở chi tiết đơn (khiếu nại, hành trình, ảnh bằng chứng) của chi nhánh khác bị chặn.
- Đã lọc: tra soát đơn, tra cứu nhiều vận đơn ("Thuộc chi nhánh khác"), cảnh báo, khiếu nại (danh sách và khiếu nại quá hạn), đối soát COD (theo chi nhánh của Shipper, cả xem và duyệt phiếu), báo cáo + CSV, hiệu suất Shipper, hồ sơ vận hành, dashboard Manager.
- **Quy tắc chuyển tiếp:** đơn cũ chưa có `order_route` và nhân sự chưa gán chi nhánh **hiện cho mọi chi nhánh** cho tới khi chạy bước 16. Đơn mới luôn có tuyến.

**Bước 11 (B8):** `OperationsCaseServiceImpl`
- Admin **không lập** 3 loại phiếu tiền và **không lập** giao lại vượt giới hạn (ném `AccessDeniedException`), vẫn lập giao lại trong giới hạn và hoàn hàng. Giao lại trong giới hạn và hoàn hàng: lập là có hiệu lực.
- Nhãn mới trong `OperationsCase.Kind` (kèm `getGroup()` cho optgroup, `isFinancial()`): Giao lại, Hoàn hàng về người gửi (nhóm "Xử lý hàng"); Shipper nộp bù tiền thiếu, Trả lại tiền Shipper nộp thừa (nhóm "Đối soát COD với Shipper"); Bồi hoàn cước (nhóm "Chi trả cho khách").
- Danh sách có lọc loại (optgroup), trạng thái, chi nhánh; "Chờ duyệt" luôn đứng đầu; Admin mở trang lần đầu mặc định "Chờ duyệt".
- Bảng phân quyền trên `admin/dashboard.html` đã cập nhật.

**Bước 12 (một phần):**
- `OrderServiceImpl.buildTimeline`: mốc "Chờ bạn mang hàng tới chi nhánh" / "Đã nhận tại quầy ...", và với đơn liên tỉnh có thêm "Tại kho gửi", "Đang trung chuyển", "Tới kho nhận".
- `OrderRoutingService.summary(order)` trả `RouteSummary` (chi nhánh gửi → nhận, vị trí hiện tại, thời gian dự kiến từ `route_tariff.lead_time_text`, địa chỉ quầy). Đã hiện ở trang tra cứu Guest (`tra-cuu.html`) và chi tiết đơn người gửi (`user/order-detail.html`).

### 2.3. Kết quả test gần nhất

Đã chạy riêng các lớp test liên quan và đạt hết (StaffSecurityIntegrationTest, ManagerOperationsServiceImplTest, ManagerOrderServiceImplTest, StaffWorkbenchServiceImplTest, StaffDashboardChartServiceImplTest, ManagerDashboardServiceImplTest, OperationsControlsIntegrationTest, CrossRoleSynchronizationIntegrationTest, LogisticsIntegrationTest, ShipperOperationsIntegrationTest, StaffWorkspaceIntegrationTest, DropOffIntegrationTest, BranchSchemaSqlTest, HubServiceTest, TrackingServiceImplTest, UpgradeAivenBranchesTest, AivenDatabaseToolsTest, ControlsValidationTest).
**Chưa chạy lại full `mvn test` sau bước 10–11.** Trước đó có 2 lỗi sẵn (không do kế hoạch này): `AivenOperationsIntegrationTest.realQueriesAssignmentTariffAndAuditUseSharedSchema` và `AivenControlsIntegrationTest.returnWorkflowLocksRealRowsAndPreservesHistory`. Hai test Aiven này cũng sẽ lỗi validate schema cho tới khi apply `UpgradeAivenDropOff`.

---

## 3. Việc còn lại (làm lần lượt, xong mỗi bước thì báo tôi rồi mới sang bước sau)

### Bước 12 (phần còn lại): trợ lý Groq và email có thông tin kho
- `service/assistant/impl/AssistantToolServiceImpl`: công cụ tra đơn trả thêm tuyến, vị trí hiện tại và thời gian dự kiến. Dùng lại `OrderRoutingService.summary(order)`, không viết logic vị trí mới.
- Email cập nhật đơn (`SenderOrderMailListener` + `SenderOrderUpdateEvent.Kind`): các Kind kho (`AT_ORIGIN_HUB`, `IN_LINEHAUL`, `AT_DEST_HUB`, `RECEIVED_AT_COUNTER`) đã có; bổ sung tên kho/chi nhánh vào nội dung nếu mẫu email hiện chưa hiện.
- Test: trợ lý trả đúng vị trí cho đơn liên tỉnh ở `LINEHAUL`; email có tên kho.

### Bước 13: thống kê Shipper tách "đã lấy" và "đã giao"
- COD: shipper giao thu (đúng sẵn vì `order.shipper` là shipper giao lúc `DELIVERED`). Phiếu nộp tiền đã lọc theo chi nhánh của Shipper ở bước 10, không cần sửa.
- Đơn liên tỉnh: khi bàn giao kho, `order.shipper` bị gỡ, người lấy chỉ còn ở `order_route.pickup_shipper_id`. Thống kê shipper (`ShipperStatisticsService`), CSV thống kê, và trang `/manager/hieu-suat-shipper` (`OperationsOverviewRepository.performance`, `ShipperPerformanceResponse`) phải có thêm cột **"Đã lấy"** đếm theo `pickupShipper`, cột "Đã giao" giữ theo `order.shipper` + `DELIVERED`.
- Test: shipper SGN lấy đơn SGN→HAN, bàn giao kho, vẫn được tính 1 "đã lấy" và 0 "đã giao".

### Bước 14: lộ trình hôm nay theo B6 (`service/tracking/ShipperRouteService.today`)
Hiện tại giữ nguyên thứ tự danh sách và gộp điểm cùng địa chỉ. Viết lại phần sắp xếp:
1. Lấy tuyến của các đơn: thêm `OrderRouteRepository` truy vấn `join fetch pickupArea, deliveryArea` theo danh sách id.
2. Tọa độ điểm: đơn chờ lấy dùng `pickupArea`, đơn đang giao dùng `deliveryArea` (`ProvinceArea.lat/lng`).
3. Điểm xuất phát: kho chi nhánh của shipper (`StaffBranchRepository.findByUser_Id` → `branch.hubAreaCode` → `ProvinceAreaRepository.findByAreaCode`). **Không dùng địa chỉ nhà shipper.**
4. Nhóm độ gấp theo B6: (1) Hỏa tốc hoặc đến hẹn giao lại; (2) sắp quá hạn theo Chính sách vận hành; (3) còn lại. Mỗi điểm dừng lấy nhóm gấp nhất trong các đơn của nó.
5. Trong từng nhóm: láng giềng gần nhất bằng `RouteDistance.kilometers` (Haversine), xuất phát từ kho, nhóm sau đi tiếp từ điểm cuối nhóm trước. Không gọi API bản đồ trả phí.
6. Đơn cũ chưa có tuyến: giữ thứ tự cũ, xếp cuối. Test `ShipperOperationsIntegrationTest.routeGroupsStopsByAddressAndUsesPickupOrDeliveryAddress` dùng đơn chưa có tuyến và shipper chưa gán chi nhánh, phải vẫn đạt.
7. Đánh số lại điểm dừng sau khi xếp. Nếu biết kho thì chặng đầu Google Maps có `origin` là địa chỉ kho, và trang `shipper/route.html` ghi "Xuất phát từ kho ...". Thêm trường vào record `ShipperRoute` nếu cần (chỉ service này tạo record).
8. "Giao thất bại liên tỉnh thì đơn về `DEST_HUB`" (bảng C bước 14): luồng giao lại hiện tại giữ shipper cũ để giao lại. **Hỏi tôi trước** nếu muốn đổi sang trả đơn về kho chờ phân công lại; mặc định không đổi chặng, chỉ hiện vị trí "kho ... đang xử lý" (đã có trong `OrderRoutingService.location`).
- Test đơn vị cho thuật toán sắp xếp (nhóm gấp đứng trước; trong nhóm đi điểm gần trước; xuất phát từ kho).

### Bước 15: báo cáo và hiệu suất theo chi nhánh
Phần lọc chi nhánh đã xong ở bước 10. Còn lại: kiểm lại CSV báo cáo có ghi chi nhánh đã lọc (hiện ghi `Ma_chi_nhanh`), và cột "Đã lấy" của bước 13 hiện trên trang hiệu suất.

### Bước 16: công cụ chuyển dữ liệu cũ (B10) và tài khoản demo theo chi nhánh
Công cụ mới trong `tool/database/` theo đúng mẫu `UpgradeAivenBranches`/`UpgradeAivenDropOff` (`--print-schema`/`--check`/`--apply`, `GET_LOCK`, timeout từng câu, chỉ thêm, chạy lại không lỗi, test bằng JDBC giả):
- `--check` in bảng ánh xạ để tôi duyệt: mỗi đơn chưa có `order_route` → chi nhánh/khu vực lấy và giao suy từ từ khóa địa chỉ ("TP.HCM / Hồ Chí Minh / Thủ Đức / Quận x" → SGN, "Hà Nội" → HAN, "Đà Nẵng" → DAD; khu vực theo tên quận nếu khớp, không khớp thì khu vực kho `hub_area_code`).
  `stage` suy từ trạng thái: `PENDING_PICKUP` → `PICKUP`; `PICKED_UP`/`IN_TRANSIT`/`FAILED`/`DELIVERED`/`RETURNED` nội tỉnh → `LAST_MILE` (liên tỉnh đang chạy thì liệt kê để xử lý tay). `pickup_shipper_id`/`delivery_shipper_id` = `shipment_order.shipper_id` hiện tại.
- Đơn ngoài 3 tỉnh đã kết thúc: gán chi nhánh gần nhất để báo cáo không lỗi. Đơn đang chạy ngoài vùng: chỉ liệt kê, không gán.
- Gán chi nhánh và khu vực phụ trách (`staff_branch`, `shipper_zone`): `shipper1`, `shipper2`, `manager1` → SGN; `shipper3` → HAN.
- Tạo tài khoản `manager_han`, `manager_dad`, `shipper_han2`, `shipper_dad1` kèm chi nhánh và khu vực. Đọc cách công cụ tài khoản cũ (`UpgradeAivenAccounts`) tạo `app_user` + `staff_account` + mật khẩu băm và sao lưu vào `config/demo-account-backups/` (đã gitignore). Mật khẩu: hỏi tôi, không tự đặt rồi in ra chat.
- Chỉ chạy `--check`, gửi tôi kết quả, **dừng chờ tôi apply** (có thể gom với `UpgradeAivenDropOff`).

### Bước 17 (tùy chọn, làm sau cùng): hoàn hàng liên tỉnh theo B9
Hỏi tôi trước khi làm.

### Cuối cùng
1. Sau khi tôi apply: `UpgradeAivenDropOff --check` và công cụ bước 16 `--check` phải báo đầy đủ.
2. `package -Pmomo-simulator` rồi full `mvn test`. Chỉ chấp nhận 2 lỗi có sẵn ở mục 2.3; báo số liệu thật.
3. Chạy web, kiểm bằng trình duyệt theo "KỊCH BẢN NGHIỆM THU CUỐI" trong `docs/PROMPT_CHATGPT_LIEN_TINH.md`, cộng thêm:
   - Tạo đơn "Tự mang ra chi nhánh" SGN → HAN: cước ước tính bằng cước tạo đơn và ít hơn 3.000 đ so với shipper đến lấy; đơn không có ở trang Phân công; kho SGN thấy ở "Chờ khách mang hàng tới quầy"; `manager_han` không nhận được; nhận tại quầy xong đơn vào danh sách chờ xếp chuyến; hành trình người gửi hiện đúng các mốc.
   - `manager1` không thấy đơn HAN → DAD ở tra soát, cảnh báo, khiếu nại, báo cáo; Admin lọc được từng chi nhánh.
   - `admin` không lập được phiếu tiền; `manager1` lập Bồi hoàn cước, `admin` duyệt; trang hồ sơ của Admin mở lên đang lọc "Chờ duyệt".
4. Báo tôi danh sách file đã đổi và kết quả. Không commit.

---

## 4. Bẫy đã gặp (đọc trước khi code)

- **Mockito trả `0L` cho phương thức trả `Long`**, không phải `null`. Mock `BranchScope` mặc định làm `of()` trả 0 (tức chi nhánh 0) → lọc mất dữ liệu trong test. Muốn "Admin xem tất cả" thì `mock(BranchScope.class, invocation -> null)` hoặc stub rõ.
- **`StaffSecurityIntegrationTest` là `@WebMvcTest`** nạp nhiều controller Manager/Admin với `@MockBean` từng service. Thêm tham số constructor vào các controller trong danh sách đó thì phải thêm `@MockBean`. Cách đã dùng: để phụ thuộc mới trong service, controller chỉ gọi service. Đổi chữ ký method service thì sửa luôn các `when(...)` trong test này.
- Phương thức `default` trên interface bị mock **không** chạy code thật: controller gọi bản mới thì test phải stub bản mới.
- Hằng JPQL ghép vào `@Query` phải là hằng biên dịch (`String` hằng trong interface), không gọi hàm được. Text block `"""` phải xuống dòng ngay sau dấu mở.
- Cột `ENUM` của MySQL map bằng `@Nationalized @Enumerated(EnumType.STRING) @Column(length = 20)`, giống `OrderRoute.stage`; bảng mới thêm cột thì `BranchSchemaSqlTest` phải chạy cả câu ALTER tương ứng.
- Test tích hợp dùng H2 (`isolated-logistics`, `create-drop`) và `support/RoutingFixtures` để nạp chi nhánh/khu vực thật, gán nhân sự SGN, gắn tuyến cho đơn.
- Thymeleaf: thứ tự ưu tiên `th:each` > `th:if` > `th:with`; cần biến `th:with` trong điều kiện thì bọc `th:block`.
- Sửa file bằng heredoc trong bash dễ hỏng ký tự `\` và dấu nháy. An toàn hơn: viết script ra file rồi chạy, hoặc dùng công cụ sửa file.
- PowerShell 5.1: file `.ps1` phải có BOM; không dùng `&&`.
- `mvn spring-boot:run` đọc template từ `target/classes`: sửa HTML/JS phải khởi động lại app.
