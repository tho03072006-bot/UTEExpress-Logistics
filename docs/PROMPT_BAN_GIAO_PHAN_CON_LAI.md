# BÀN GIAO: LÀM NỐT TOÀN BỘ KẾ HOẠCH LIÊN TỈNH 3 CHI NHÁNH

Bạn tiếp quản code trên nhánh **`feature_Tho_lien_tinh`** (GitHub `tho03072006-bot/UTEExpress-Logistics`),
tách ra từ `develop` tại `4ce5661`. Trên máy chủ repo, thư mục là `D:\WEB\Project_CK` (Windows).

Đọc thêm: `docs/PROMPT_CHATGPT_LIEN_TINH.md` (quyết định đã chốt B1–B11, bảng bước ở phần C, kịch bản nghiệm thu cuối)
và `docs/KE_HOACH_LIEN_TINH.md` (kế hoạch gốc). Hai file đó viết theo kiểu "làm từng bước, chờ tôi gõ tiếp".
**File này thay thế cách làm đó:** chỗ nào mâu thuẫn thì theo file này.

---

## 0. Chế độ làm việc: làm liền một mạch tới khi xong

- Làm hết mục 3 theo thứ tự, **không dừng chờ xác nhận giữa các bước**. Mỗi bước: code → chạy test liên quan → commit → push → sang bước sau.
- Mọi chỗ trước đây ghi "hỏi tôi" đều đã có **phương án mặc định** trong file này. Cứ làm theo mặc định và ghi lại trong báo cáo cuối.
- **Chỉ được dừng khi gặp một trong các trường hợp sau.** Gặp thì báo rõ lý do và việc tôi cần làm:
  1. Thiếu file cấu hình bí mật (mục 1.3) nên không kết nối được Aiven.
  2. `--check` báo cột hoặc bảng **đã có nhưng sai định nghĩa**, hoặc `--apply` lỗi giữa chừng.
  3. Muốn đạt yêu cầu thì buộc phải sửa `Order.java`, `AppUser.java` hoặc enum `OrderStatus`.
  4. Test đỏ mà sửa đúng thì phải đổi một quyết định nghiệp vụ đã chốt.

---

## 1. Quy tắc bắt buộc

1. **Git:** chỉ làm trên `feature_Tho_lien_tinh`. Được commit và push lên chính nhánh này. Không tạo nhánh khác, không merge `develop` hay nhánh nào khác vào, không mở hoặc merge PR, không `rebase`, `reset`, `stash`, `clean`, `checkout --`, không force push.
   Commit message dạng `<loại>: <mô tả không dấu>`, ví dụ `feat: lo trinh shipper theo khu vuc`. **Không ghi tên công cụ AI** vào commit, code hay comment. Không commit thư mục `.claude/` hay file bí mật.
2. **Aiven là DB dùng chung cả nhóm.** Chỉ được ghi bằng các công cụ migration trong `tool/database/`, theo đúng thứ tự: `--check` → `--apply` → `--check` lại.
   Được `--apply` khi `--check` chỉ báo **thiếu** (cột/bảng/dòng chưa có). Công cụ chỉ được `CREATE ... IF NOT EXISTS`, `ADD COLUMN`, `INSERT ... WHERE NOT EXISTS`; tuyệt đối không `UPDATE`, `DELETE`, `DROP`, `TRUNCATE` dữ liệu có sẵn.
3. **File bí mật:** `config/*-local.properties`, `src/main/resources/application-local.properties`, `config/certificates/*`. Không mở ra đọc, không in, không sửa, không commit. Máy mới thì chủ repo gửi riêng, bạn chỉ đặt đúng chỗ.
4. **Không sửa** `entity/Order.java`, `entity/AppUser.java`, giá trị enum `OrderStatus`. Cần thêm dữ liệu thì tạo bảng/entity riêng.
5. Không `@Disabled`, không skip test, không đổi cấu hình test để cho xanh. Báo số liệu thật.
6. Giao diện, comment, thông báo, email: **tiếng Việt có dấu**. Màu chỉ xanh `#0072BC`, trắng, xám. Lỗi dùng `.ute-field-error`.
7. Kiến trúc 3 tầng: controller không gọi repository; mọi kiểm tra quyền ở server.
8. **Tài khoản demo có sẵn** (`admin`, `manager1`, `shipper1..3`, các USER): chỉ đăng nhập để kiểm, không đổi mật khẩu. Được gán chi nhánh cho tài khoản nội bộ ở bước 16.

Lệnh build (PowerShell; máy khác thì trỏ `JAVA_HOME` tới JDK 21 của máy đó):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -q -B test-compile
mvn -q -B test "-Dtest=TenTest1,TenTest2" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -q -B -Pmomo-simulator "-Dmaven.test.skip=true" package   # cần trước khi chạy full test (test smoke MoMo)
mvn -q -B test
```

Với `-q`, test đạt thì không in gì. Muốn chắc, đọc số liệu trong `target/surefire-reports/TEST-*.xml`.

Chạy công cụ migration:

```powershell
mvn -q -B compile dependency:build-classpath "-Dmdep.outputFile=target/branches-classpath.txt"
$cp = 'target/classes;' + (Get-Content target/branches-classpath.txt -Raw).Trim()
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $cp vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenDropOff --check
```

---

## 2. Trạng thái khi bàn giao (tối 23/10/2026)

Bước 1–11 trong bảng C **đã có code và test đạt** (commit `18db294`, `10eb106`, `8fa54f1`). Bước 12 làm được một phần. Bước 13–17 và kiểm tra cuối **chưa làm**.

### 2.1. DB chưa có 2 cột mới nên web chưa chạy được với Aiven

Entity đã có `OrderRoute.pickupMode` và `OperationsPolicy.dropOffDiscount`, nhưng `tool/database/UpgradeAivenDropOff` **chưa apply**.
App dùng `ddl-auto=validate` nên chưa khởi động được với Aiven. Đây là việc đầu tiên của mục 3.
Migration đã có `--print-schema`, `GET_LOCK`, timeout từng câu, chạy lại không lỗi, test `UpgradeAivenDropOffTest`:

```sql
ALTER TABLE order_route ADD COLUMN pickup_mode ENUM('COURIER','DROP_OFF') NOT NULL DEFAULT 'COURIER';
ALTER TABLE operations_policy ADD COLUMN drop_off_discount DECIMAL(12,0) NOT NULL DEFAULT 3000;
```

`ADD COLUMN` kèm giá trị mặc định chạy được khi app của thành viên khác đang mở; code cũ không đọc cột mới nên không ảnh hưởng.
Trên máy chủ repo có thể còn một app cũ giữ cổng 8080. Nếu cổng 8080 bận thì chạy app mới ở cổng khác (`--server.port=8081`), **không tắt** tiến trình của người khác.

### 2.2. `develop` vừa có cách tính cước thứ hai: đợt này KHÔNG gộp

Sau khi tách nhánh, Tài push lên `develop` commit `c097b85` (phụ phí khoảng cách đường chim bay giữa hai địa chỉ, lấy tọa độ qua ArcGIS) và `f2b4877` (test JS).
Commit này thêm cột vào `Order.java`, có `service/geo/*`, `OrderPricingService`, migration `UpgradeAivenOrderDistance` và `db/migration/V7__order_distance.sql`.
Nó **trùng 8 file** với nhánh này: `UserOrderController`, `FeeEstimateService`, `OrderDraftServiceImpl`, `OrderServiceImpl`, `shipper/order-detail.html`, `user/order-detail.html`, `user/order-form.html`, `OrderFeeEstimateTest`.

**Mặc định:** không merge `origin/develop`, giữ cách tính theo khu vực (B7: `RouteQuoteService` + `RouteDistance`).
Không chạy migration của Tài. Cuối đợt, trong báo cáo, viết mục "Gộp với develop": các file xung đột, chỗ hai cách tính chồng nhau, đề xuất cách gộp. Việc gộp để nhóm quyết khi mở PR.

### 2.3. Những gì đã làm (dùng lại, đừng viết trùng)

**Bước 7–9 (phân công, chặng shipper, kho):**
- `service/tracking/DispatchService`, `service/routing/DispatchRoutingPolicy`: phân công theo B5. `scope(actor, requested)` trả `null` = Admin xem mọi chi nhánh, `-1` = Manager chưa gán chi nhánh, ném `AccessDeniedException` khi Manager đòi chi nhánh khác.
- `entity/routing/OrderRoute`: các hàm chuyển chặng `assignShipper`, `startLastMile`, `receiveAtOriginHub`, `receiveAtCounter`, `departLinehaul`, `arriveDestHub`, `backToOriginHub`, `choosePickupMode`.
- `TrackingServiceImpl.handoverToHub` + nút "Bàn giao vào kho" ở `templates/shipper/order-detail.html`. Đơn liên tỉnh ở chặng lấy không được tự chuyển `IN_TRANSIT`.
- `service/routing/HubService` + `controller/manager/HubController` + `templates/manager/hub.html` (`/manager/kho`): tạo/hủy chuyến, niêm phong xuất kho, nhận chuyến (đối chiếu niêm phong và từng kiện), kiện thiếu ghi chú và nhận bổ sung.

**Bước 9b (Tự mang ra chi nhánh, B11):** xong toàn bộ, chỉ còn apply DB.
- `RouteQuoteService.quote(..., PickupMode)`: giảm `drop_off_discount`, không vượt tổng (tổng không âm), mã giảm giá áp sau. `Quote` có thêm `dropOffDiscount`, `pickupMode`.
- `OrderCreateRequest.pickupMode`, `FeeEstimateRequest.pickupMode`; radio ở `user/order-form.html` và `uoc-tinh-cuoc.html`; `static/js/route-quote.js` gửi `pickupMode`, hiện địa chỉ quầy, khóa nút "Thêm vào giỏ" khi chọn tự mang.
- Giỏ đơn **không nhận** DROP_OFF (`OrderDraftServiceImpl.addDraft` báo lỗi rõ). `OrderSubmissionGuard.fingerprint` chỉ thêm "DROP_OFF" khi tự mang, để mã băm đơn cũ không đổi.
- Hàng chờ phân công (`OrderRouteRepository.DISPATCHABLE`) và cảnh báo "Chưa phân công" bỏ đơn DROP_OFF. Cảnh báo "Chậm lấy" ghi "Khách chưa mang hàng tới chi nhánh".
- `HubService.receiveAtCounter` + mục "Chờ khách mang hàng tới quầy" trên trang kho. Nội tỉnh → `PICKED_UP/DEST_HUB`, liên tỉnh → `PICKED_UP/ORIGIN_HUB`. Ghi lịch sử, nhật ký `COUNTER_RECEIVE`, email `RECEIVED_AT_COUNTER`.
- Admin sửa mức giảm (0–20.000 đ) ở `/admin/chinh-sach-van-hanh`; nhật ký ghi mức cũ → mới.
- Test: `UpgradeAivenDropOffTest` (7), `RouteQuoteServiceTest` (25), `DropOffIntegrationTest` (5, H2).

**Bước 10 (Manager chỉ thấy dữ liệu chi nhánh mình), các mảnh dùng chung:**
- `OrderRouteRepository.BRANCH_SCOPE`: điều kiện JPQL cho đơn có bí danh `o`, tham số `:branchId`.
- `StaffBranchRepository.USER_SCOPE`: điều kiện JPQL cho nhân sự có bí danh `u` (Shipper của chi nhánh).
- `service/routing/BranchScope`: `of(actor, requested)`, `branches(actor)`, `visible(actor, orders)`, `requireOrder(actor, order)`, `requireShipper(actor, shipper)`, `isAdmin(actor)`.
- `fragments/branch-filter.html`: `select(branches, branchId)` (ô chọn chỉ hiện với Admin) và `note(branches)`.
- `LogisticsAccess.checkOrder`: chặn Manager mở chi tiết đơn (khiếu nại, hành trình, ảnh bằng chứng) của chi nhánh khác.
- Đã lọc: tra soát đơn, tra cứu nhiều vận đơn ("Thuộc chi nhánh khác"), cảnh báo, khiếu nại (danh sách và quá hạn), đối soát COD (theo chi nhánh của Shipper, cả xem và duyệt phiếu), báo cáo + CSV, hiệu suất Shipper, hồ sơ vận hành, dashboard Manager.
- **Quy tắc chuyển tiếp:** đơn cũ chưa có `order_route` và nhân sự chưa gán chi nhánh **hiện cho mọi chi nhánh** cho tới khi xong bước 16. Đơn mới luôn có tuyến.

**Bước 11 (B8), trong `OperationsCaseServiceImpl`:**
- Admin **không lập** 3 loại phiếu tiền và **không lập** giao lại vượt giới hạn (ném `AccessDeniedException`); vẫn lập được giao lại trong giới hạn và hoàn hàng. Giao lại trong giới hạn và hoàn hàng: lập là có hiệu lực.
- Nhãn mới trong `OperationsCase.Kind` (kèm `getGroup()` cho optgroup và `isFinancial()`): Giao lại, Hoàn hàng về người gửi (nhóm "Xử lý hàng"); Shipper nộp bù tiền thiếu, Trả lại tiền Shipper nộp thừa (nhóm "Đối soát COD với Shipper"); Bồi hoàn cước (nhóm "Chi trả cho khách").
- Danh sách lọc được theo loại (optgroup), trạng thái, chi nhánh; "Chờ duyệt" luôn đứng đầu; Admin mở trang lần đầu mặc định lọc "Chờ duyệt". Bảng phân quyền trên `admin/dashboard.html` đã cập nhật.

**Bước 12 (một phần):**
- `OrderServiceImpl.buildTimeline`: mốc "Chờ bạn mang hàng tới chi nhánh" / "Đã nhận tại quầy ..."; đơn liên tỉnh có thêm "Tại kho gửi", "Đang trung chuyển", "Tới kho nhận".
- `OrderRoutingService.summary(order)` trả `RouteSummary` (chi nhánh gửi → nhận, vị trí hiện tại, thời gian dự kiến lấy từ `route_tariff.lead_time_text`, địa chỉ quầy). Đã hiện ở trang tra cứu Guest (`tra-cuu.html`) và trang chi tiết đơn của người gửi (`user/order-detail.html`).

### 2.4. Kết quả test khi bàn giao

Full `mvn test`: 1085 test. 1 failure đã sửa ở commit `8fa54f1`. Còn 13 error, đều là test chạy thật trên Aiven (`UteexpressApplicationTests`, `AivenAccountIntegrationTest`, `AivenControlsIntegrationTest`, `AivenOperationsIntegrationTest`):
context không nạp được vì `Schema-validation: missing column [drop_off_discount] in table [operations_policy]`.
Sau khi apply `UpgradeAivenDropOff`, kỳ vọng chỉ còn 2 lỗi có sẵn từ trước, không do kế hoạch này: `AivenOperationsIntegrationTest.realQueriesAssignmentTariffAndAuditUseSharedSchema` và `AivenControlsIntegrationTest.returnWorkflowLocksRealRowsAndPreservesHistory`.

---

## 3. Việc còn lại (làm liền, theo thứ tự)

### Bước A: apply `UpgradeAivenDropOff`
`--check` (phải báo đúng 2 cột "còn thiếu") → `--apply` → `--check` lại (phải báo "Schema đã đầy đủ"). Sau đó chạy lại 4 lớp test Aiven ở mục 2.4.

### Bước 12 (phần còn lại): trợ lý Groq và email có thông tin kho
- `service/assistant/impl/AssistantToolServiceImpl`: công cụ tra đơn trả thêm tuyến, vị trí hiện tại, thời gian dự kiến. Dùng lại `OrderRoutingService.summary(order)`, không viết logic vị trí mới.
- Email cập nhật đơn (`service/tracking/impl/SenderOrderMailListener` + `SenderOrderUpdateEvent.Kind`): các Kind kho (`AT_ORIGIN_HUB`, `IN_LINEHAUL`, `AT_DEST_HUB`, `RECEIVED_AT_COUNTER`) đã có. Bổ sung tên kho/chi nhánh vào nội dung nếu mẫu email chưa hiện.
- Test: trợ lý trả đúng vị trí cho đơn liên tỉnh đang `LINEHAUL`; email có tên kho.

### Bước 13: thống kê Shipper tách "đã lấy" và "đã giao"
- COD: shipper giao thu tiền (đúng sẵn, vì `order.shipper` là shipper giao lúc `DELIVERED`). Phiếu nộp tiền đã lọc theo chi nhánh của Shipper ở bước 10.
- Đơn liên tỉnh: lúc bàn giao kho, `order.shipper` bị gỡ, người lấy chỉ còn lưu ở `order_route.pickup_shipper_id`.
  Thống kê shipper (`ShipperStatisticsService`), CSV thống kê và trang `/manager/hieu-suat-shipper` (`OperationsOverviewRepository.performance`, `ShipperPerformanceResponse`) thêm cột **"Đã lấy"** đếm theo `pickupShipper`. Cột "Đã giao" giữ theo `order.shipper` + `DELIVERED`.
- Test: shipper SGN lấy đơn SGN→HAN rồi bàn giao kho thì được tính 1 "đã lấy", 0 "đã giao".

### Bước 14: lộ trình hôm nay theo B6 (`service/tracking/ShipperRouteService.today`)
Hiện tại service giữ nguyên thứ tự danh sách và gộp điểm cùng địa chỉ. Viết lại phần sắp xếp:
1. Lấy tuyến của các đơn: thêm truy vấn ở `OrderRouteRepository` theo danh sách id, `join fetch pickupArea, deliveryArea`.
2. Tọa độ điểm: đơn chờ lấy dùng `pickupArea`, đơn đang giao dùng `deliveryArea` (`ProvinceArea.lat/lng`).
3. Điểm xuất phát là kho chi nhánh của shipper (`StaffBranchRepository.findByUser_Id` → `branch.hubAreaCode` → `ProvinceAreaRepository.findByAreaCode`). **Không dùng địa chỉ nhà shipper.**
4. Nhóm độ gấp: (1) Hỏa tốc hoặc đến hẹn giao lại; (2) sắp quá hạn theo Chính sách vận hành; (3) còn lại. Mỗi điểm dừng lấy nhóm gấp nhất trong các đơn của nó.
5. Trong từng nhóm, đi láng giềng gần nhất bằng `RouteDistance.kilometers` (Haversine), xuất phát từ kho; nhóm sau đi tiếp từ điểm cuối của nhóm trước. Không gọi API bản đồ trả phí.
6. Đơn cũ chưa có tuyến giữ thứ tự cũ, xếp cuối. Test `ShipperOperationsIntegrationTest.routeGroupsStopsByAddressAndUsesPickupOrDeliveryAddress` (đơn chưa có tuyến, shipper chưa gán chi nhánh) phải vẫn đạt.
7. Đánh số lại điểm dừng sau khi xếp. Biết kho thì chặng đầu Google Maps có `origin` là địa chỉ kho, và trang `shipper/route.html` ghi "Xuất phát từ kho ...". Thêm trường vào record `ShipperRoute` nếu cần (chỉ service này tạo record).
8. Giao thất bại liên tỉnh: **mặc định giữ luồng giao lại hiện có** (shipper cũ giao lại theo hồ sơ), không đổi chặng. Vị trí hiển thị "Giao chưa thành công, kho ... đang xử lý" đã có trong `OrderRoutingService.location`.
- Test đơn vị cho thuật toán: nhóm gấp đứng trước; trong nhóm đi điểm gần trước; xuất phát từ kho.

### Bước 15: báo cáo và hiệu suất theo chi nhánh
Lọc chi nhánh đã xong ở bước 10. Còn lại: CSV báo cáo ghi tên chi nhánh đã lọc (hiện chỉ ghi `Ma_chi_nhanh`), và trang hiệu suất có cột "Đã lấy" của bước 13.

### Bước 16: chuyển dữ liệu cũ (B10) và nhân sự theo chi nhánh
**16a. Công cụ `tool/database/UpgradeAivenLegacyRoutes`**, theo đúng mẫu `UpgradeAivenBranches`/`UpgradeAivenDropOff` (`--print-schema`/`--check`/`--apply`, `GET_LOCK`, timeout từng câu, chỉ `INSERT ... WHERE NOT EXISTS`, chạy lại không lỗi, test bằng JDBC giả):
- `--check` in bảng ánh xạ: mỗi đơn chưa có `order_route` → chi nhánh/khu vực lấy và giao suy từ từ khóa trong địa chỉ.
  "TP.HCM / Hồ Chí Minh / Thủ Đức / Quận x" → SGN, "Hà Nội" → HAN, "Đà Nẵng" → DAD. Khu vực theo tên quận nếu khớp danh mục, không khớp thì lấy khu vực kho `hub_area_code`.
- `stage` suy từ trạng thái: `PENDING_PICKUP` → `PICKUP`; các trạng thái còn lại của đơn nội tỉnh → `LAST_MILE`.
  `pickup_shipper_id` = `delivery_shipper_id` = `shipment_order.shipper_id` hiện tại. `pickup_mode` để mặc định `COURIER`.
- Đơn liên tỉnh **đang chạy**, và đơn **đang chạy ngoài 3 tỉnh**: không thêm, chỉ liệt kê trong báo cáo để Manager xử lý tay.
  Đơn ngoài 3 tỉnh **đã kết thúc**: gán chi nhánh gần nhất để báo cáo không lỗi.
- Lưu kết quả `--check` vào `target/legacy-routes-check.txt` (không commit), rồi `--apply` → `--check` lại.

**16b. Nhân sự:** làm trên web bằng tài khoản `admin`, trang `/admin/quan-ly-tai-khoan` (đã có ô chọn chi nhánh và khu vực phụ trách). Không viết SQL tạo tài khoản.
- Gán chi nhánh và khu vực cho tài khoản có sẵn: `manager1`, `shipper1`, `shipper2` → SGN; `shipper3` → HAN. Chỉ đổi chi nhánh/khu vực, không đổi mật khẩu.
- Tạo mới: `manager_han` (HAN), `manager_dad` (DAD), `shipper_han2` (HAN), `shipper_dad1` (DAD), mỗi shipper có khu vực chính.
  Mật khẩu do chủ repo ghi trong prompt gửi kèm. Không ghi mật khẩu vào code, commit hay file trong repo. Hệ thống bắt đổi mật khẩu lần đầu thì đổi sang mật khẩu cuối cùng trong prompt.
- Không có công cụ trình duyệt thì gửi form bằng HTTP có session và CSRF (PowerShell `Invoke-WebRequest -WebSession`), không ghi thẳng DB.

### Bước 17: hoàn hàng liên tỉnh theo B9 (làm sau cùng)
Chỉ làm khi bước 12–16 đã xong và full test đạt. Dùng lại màn hình kho theo chiều ngược, không thêm bảng mới; nếu buộc thêm cột/bảng thì viết migration riêng theo quy tắc 1.2.
Nếu không làm kịp mà vẫn giữ được các luồng khác chạy đúng thì bỏ qua và ghi rõ trong báo cáo.

### Bước cuối: kiểm tra toàn bộ
1. `UpgradeAivenDropOff --check` và `UpgradeAivenLegacyRoutes --check` đều báo đầy đủ.
2. `package -Pmomo-simulator` rồi full `mvn test`. Chỉ chấp nhận 2 lỗi có sẵn ở mục 2.4; báo số liệu thật.
3. Chạy web, kiểm theo "KỊCH BẢN NGHIỆM THU CUỐI" trong `docs/PROMPT_CHATGPT_LIEN_TINH.md`, cộng thêm:
   - Đơn "Tự mang ra chi nhánh" SGN → HAN: cước trang ước tính bằng cước lúc tạo đơn và ít hơn 3.000 đ so với shipper đến lấy; đơn không có ở trang Phân công; kho SGN thấy ở "Chờ khách mang hàng tới quầy"; `manager_han` không nhận tại quầy được; nhận xong đơn vào danh sách chờ xếp chuyến; hành trình người gửi hiện đúng các mốc.
   - `manager1` không thấy đơn HAN → DAD ở tra soát, cảnh báo, khiếu nại, báo cáo; Admin lọc được từng chi nhánh.
   - `admin` không lập được phiếu tiền; `manager1` lập Bồi hoàn cước, `admin` duyệt được; trang hồ sơ của Admin mở lên đang lọc "Chờ duyệt".
   - Đơn kiểm thử tạo trên Aiven phải ghi rõ là đơn kiểm thử, liệt kê mã vận đơn trong báo cáo.
   Có công cụ trình duyệt thì kiểm bằng trình duyệt; không có thì bằng HTTP có session. Ghi rõ đã kiểm bằng cách nào.
4. Commit, push phần còn lại. `git status` phải sạch, trừ `.claude/`.
5. **Báo cáo cuối** gồm: danh sách commit; các migration đã apply và kết quả `--check`; số liệu full test; kết quả từng kịch bản nghiệm thu; tài khoản đã tạo/gán (không ghi mật khẩu); mã các đơn kiểm thử; danh sách đơn cũ phải xử lý tay; mục "Gộp với develop" (mục 2.2); việc chưa làm được và lý do.

---

## 4. Bẫy đã gặp (đọc trước khi code)

- **Mockito trả `0L` cho phương thức trả `Long`**, không phải `null`. Mock `BranchScope` mặc định làm `of()` trả 0 (tức chi nhánh 0) nên lọc mất dữ liệu trong test. Muốn "Admin xem tất cả" thì `mock(BranchScope.class, invocation -> null)` hoặc stub rõ.
- **`StaffSecurityIntegrationTest` là `@WebMvcTest`** nạp nhiều controller Manager/Admin với `@MockBean` cho từng service. Thêm tham số constructor vào các controller đó thì phải thêm `@MockBean`. Cách đã dùng: để phụ thuộc mới trong service, controller chỉ gọi service. Đổi chữ ký method service thì sửa luôn các `when(...)` trong test này.
- Phương thức `default` trên interface bị mock **không** chạy code thật: controller gọi bản mới thì test phải stub bản mới.
- **`StaffQueryValidationTest`** dựng Hibernate với vài entity để kiểm tra câu `@Query`: truy vấn mới tham chiếu entity nào thì đăng ký thêm entity đó.
- Hằng JPQL ghép vào `@Query` phải là hằng biên dịch (`String` hằng trong interface), không gọi hàm được. Text block `"""` phải xuống dòng ngay sau dấu mở.
- Cột `ENUM` của MySQL map bằng `@Nationalized @Enumerated(EnumType.STRING) @Column(length = 20)`, giống `OrderRoute.stage`. Thêm cột vào bảng của `BranchSchema` thì `BranchSchemaSqlTest` phải chạy cả câu ALTER tương ứng.
- Test tích hợp dùng H2 (`isolated-logistics`, `create-drop`) và `support/RoutingFixtures` để nạp chi nhánh/khu vực thật, gán nhân sự SGN, gắn tuyến cho đơn.
- Tài khoản Manager mới tạo bị bắt đổi mật khẩu ở lần đăng nhập đầu (`passwordChangeRequired`).
- Thymeleaf: thứ tự ưu tiên `th:each` > `th:if` > `th:with`; cần biến `th:with` trong điều kiện thì bọc `th:block`.
- Sửa file bằng heredoc trong bash dễ hỏng ký tự `\` và dấu nháy. An toàn hơn: viết script ra file rồi chạy, hoặc dùng công cụ sửa file.
- PowerShell 5.1: file `.ps1` phải có BOM; không dùng `&&`.
- `mvn spring-boot:run` đọc template từ `target/classes`: sửa HTML/JS phải khởi động lại app.
- Seed hoặc migration qua mạng tới Aiven mất khoảng 0,75 giây mỗi câu: đặt timeout riêng cho từng câu, không đặt chung cho cả batch.
