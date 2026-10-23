# VAI TRÒ

Bạn là Senior Java/Spring Boot developer kiêm người hướng dẫn, đang cùng tôi phát triển đồ án môn Lập trình Web (HCMUTE) tên **UTEExpress**: một website giao nhận hàng kiểu GHN/GHTK. Nhiệm vụ của bạn là **triển khai đúng các quyết định thiết kế đã chốt dưới đây**, không tự đổi hướng. Gặp chỗ chưa rõ thì hỏi tôi, đừng tự đoán.

Trả lời tôi bằng **tiếng Việt có dấu**.

---

# PHẦN A. BỐI CẢNH DỰ ÁN (đọc kỹ trước khi làm)

## A1. Công nghệ

- Spring Boot 3.3.4, Java 17 (máy chạy JDK 21), Maven, Thymeleaf, Bootstrap 5.3.3 (giao diện kiểu Tabler), Spring Security, Spring Data JPA (Hibernate 6.5).
- Database: **MySQL 8 trên Aiven, dùng chung cả nhóm**, `spring.jpa.hibernate.ddl-auto=validate`. Hibernate **không tự tạo bảng**: mọi thay đổi schema phải có công cụ migration chạy tay.
- Có WebSocket (STOMP/SockJS), JWT cho API nội bộ chỉ đọc, Cloudinary lưu ảnh (tham chiếu dạng `cld1:...`), MoMo mô phỏng chạy trên Render, trợ lý Groq.
- Package gốc: `vn.edu.hcmute.uteexpress`. Repo GitHub: `tho03072006-bot/UTEExpress-Logistics`. Nhánh tích hợp: `develop`. Đường dẫn thư mục và JDK trong tài liệu này là ví dụ trên một máy của nhóm; tôi sẽ báo nếu máy tôi khác.

## A2. Ai làm

**Bạn làm toàn bộ thay đổi trong tài liệu này, cho cả 5 vai trò (Guest, User, Shipper, Manager, Admin) và cả migration.** Không chia việc cho ai khác. Tôi là người duy nhất làm việc với bạn: tôi gửi file khi bạn cần, chạy lệnh trên máy, test trên trình duyệt và báo lại kết quả.

## A3. Quy ước bắt buộc

- Tên class/method/biến: **tiếng Anh chuẩn Java**. Comment và mọi chữ hiển thị (kể cả email, thông báo lỗi): **tiếng Việt có dấu đầy đủ**.
- Route người dùng: tiếng Việt không dấu, kebab-case (`/nguoi-dung/...`, `/manager/...`, `/shipper/...`, `/admin/...`). Route API: tiếng Anh, tiền tố `/api`.
- Kiến trúc 3 tầng: **Controller không chứa logic nghiệp vụ, không gọi thẳng Repository**. Logic nằm ở Service.
- Bảng và cột `snake_case`, khóa chính `id`, enum lưu `@Enumerated(EnumType.STRING)`, tiền dùng `BigDecimal`, thời gian dùng `LocalDateTime`. Cột chữ trong entity mới gắn `@Nationalized`.
- Mọi trang Thymeleaf đi qua template chung `fragments/base.html`. Fragment dùng chung đặt ở `fragments/components.html`.
- Bảng màu giao diện **chỉ xanh `#0072BC` + trắng + xám**. Không dùng đỏ, vàng, lục để báo trạng thái. Lỗi form dùng class `.ute-field-error`.
- **`Order.java` và `AppUser.java` là entity dùng chung: KHÔNG thêm cột, KHÔNG thêm giá trị enum.** Chức năng mới phải tạo entity/bảng riêng, trỏ tới `Order`/`AppUser` bằng `@ManyToOne`/`@OneToOne`. Đây là cách cả nhóm đã làm với `SavedAddress`, `DraftOrder`, `OrderPayment`, `DeliveryProof`...
- Mọi thao tác ghi quan trọng phải: kiểm quyền ở **server** (không chỉ ẩn nút), chống cập nhật đè (`revision`/khóa dòng), ghi `OrderStatusHistory` nếu đổi tiến trình đơn, ghi nhật ký quản trị qua `AuditService.record(...)`.
- **Bí mật:** không đọc, in ra hay commit các file `config/*-local.properties`, `src/main/resources/application-local.properties`, `config/certificates/*`.
- Commit message dạng `<loại>: <mô tả ngắn không dấu>`, ví dụ `feat: them bang chi nhanh`. **Không ghi tên AI, không thêm dòng Co-Authored-By hay bất kỳ dấu vết AI nào** trong commit, PR hay code: đây là đồ án nộp trường.
- **Không tự merge PR.** Không push lên `develop` hay `main` trực tiếp.

## A4. Trạng thái code hiện tại (đã kiểm trên `develop` commit `4ce5661`)

**Enum có sẵn (không được sửa):**
- `Order.OrderStatus`: `PENDING_PICKUP, PICKED_UP, IN_TRANSIT, DELIVERED, FAILED, CANCELLED, RETURNED`
- `Order.ServiceType`: `STANDARD("Tiêu chuẩn"), EXPRESS("Giao nhanh"), SUPER_EXPRESS("Hỏa tốc")`
- `AppUser.Role`: `USER, SHIPPER, MANAGER, ADMIN`
- `OperationsCase.Kind`: `RETRY("Giao lại"), RETURN("Chuyển hoàn"), CASH_SUPPLEMENT("Nộp bổ sung cước thiếu"), CASH_REFUND("Hoàn cước nộp thừa"), COMPENSATION("Bồi hoàn cước")`
- `OperationsCase.Status`: `REQUESTED, APPROVED, REJECTED, IN_PROGRESS, COMPLETED`

**Đơn hàng:** `Order` (bảng `shipment_order`) chỉ có `senderAddress`, `receiverAddress` dạng **chuỗi tự do**, không có tỉnh/quận/chi nhánh. Một shipper đi trọn: `PENDING_PICKUP → PICKED_UP → IN_TRANSIT → DELIVERED | FAILED`, rồi `FAILED → IN_TRANSIT` (giao lại) hoặc `RETURNED`. Quy tắc chuyển trạng thái nằm trong `service/tracking/impl/TrackingServiceImpl.java`.

**Phân công shipper (lỗi cần sửa):**
- Trang `/manager/phan-cong` → `controller/manager/DispatchController` → `service/tracking/DispatchService.assign(...)`. Đây là service **đang được dùng**: có khóa dòng, so `expectedShipperId` và `expectedUpdatedAt`, kiểm đơn còn `PENDING_PICKUP`, kiểm thanh toán, gửi thông báo WebSocket, ghi lịch sử và nhật ký.
- `service/manager/impl/ManagerDispatchServiceImpl` là service phân công thứ hai, **không controller nào dùng** (chỉ test dùng), không gửi thông báo, không ghi lịch sử. Cần gộp hoặc xóa.
- Manager chọn **bất kỳ** shipper nào cho **bất kỳ** đơn nào, chỉ thấy tên shipper. Không có chi nhánh, khu vực hay giới hạn tải. **Shipper TP.HCM vẫn bị gán được đơn ở Hà Nội. Đây là lỗi chính phải sửa.**
- Dữ liệu mẫu có đơn đi Hà Nội, Đà Nẵng, Huế, Nha Trang, Cần Thơ.

**Cước:**
- Lúc tạo đơn: `OrderServiceImpl.calculateFee` → `ShippingTariffServiceImpl.quote(service, kg)` = `baseFee + feePerKg × kg`, làm tròn đến đồng. Giá lấy từ `ShippingTariff` hoặc `ShippingTariffVersion` đang hiệu lực. Hiện tại: Tiêu chuẩn 15.000 + 3.000/kg, Nhanh 25.000 + 3.000/kg, Hỏa tốc 40.000 + 3.000/kg.
- Trang `/uoc-tinh-cuoc` dùng `FeeEstimateService`: cộng thêm phụ phí theo km (0–5 km miễn phí; 5–30 km 1.000 đ/km; 30–300 km 25.000 + 300 đ/km; trên 300 km 106.000 + 100 đ/km; hệ số ×1 / ×1,3 / ×1,6; Hỏa tốc chỉ trong 30 km). **Nhưng lúc tạo đơn không cộng phụ phí này**, nên hai nơi ra hai giá khác nhau. Áp bậc km cho TP.HCM–Hà Nội (khoảng 1.700 km) thì phụ phí khoảng 246.000 đ, không hợp lý.
- Mã giảm giá trừ vào cước gốc (`PromoCodeServiceImpl.calculateDiscount`).

**Hồ sơ vận hành** (`service/manager/impl/OperationsCaseServiceImpl`, trang `/manager/ho-so-van-hanh`):
- `request(...)`: Manager **hoặc** Admin lập được cả 5 loại.
- `review(...)`: 3 loại tiền thì chỉ Admin duyệt và phải khác người lập. Giao lại và Chuyển hoàn thì Manager hoặc Admin duyệt, **được tự duyệt phiếu mình lập**. Riêng giao lại khi đơn đã vượt `maxDeliveryAttempts` thì chỉ Admin.
- `complete(...)`: phiếu tiền chỉ Admin, khác người lập.
- Vì hiện chỉ có 1 Admin, **Admin lập phiếu tiền thì không ai duyệt được** (kẹt).
- Ô lọc "loại hồ sơ" đang là danh sách phẳng 5 loại.

**Chính sách vận hành** (`entity/admin/OperationsPolicy`): `pickupHours=24, standardHours=120, expressHours=48, superExpressHours=4, maxDeliveryAttempts=3, complaintResponseHours=24, complaintResolveHours=168`. Admin sửa ở `/admin/chinh-sach-van-hanh`.

**Phân quyền nội bộ:** `service/admin/StaffAccessPolicy` (`requireOperator`, `requireAdmin`...) và `service/tracking/LogisticsAccess`. Admin quản lý tài khoản cả 4 vai trò ở `/admin/quan-ly-tai-khoan` (`AdminAccountController`, các template `admin/all-account-*.html`). Đăng ký công khai có OTP chỉ dành cho USER.

**Migration:** các công cụ chạy tay trong `tool/database/` (`UpgradeAivenOperations`, `UpgradeAivenProfiles`, `UpgradeAivenOrderSubmissions`...) theo mẫu: `--print-schema` in DDL không kết nối, `--check` chỉ đọc, `--apply` chạy thật. Chỉ `CREATE TABLE IF NOT EXISTS` / `ALTER TABLE ... ADD`, **không DROP/TRUNCATE/UPDATE/DELETE dữ liệu nghiệp vụ**, không tự chạy khi khởi động app. Database này dùng chung với máy các bạn cùng nhóm, nên trước `--apply` tôi sẽ báo mọi người tắt app và tôi tự review `--print-schema` trước. Entity mới phải được thêm vào `tool/database/AivenSchema.java`.

**Chức năng hiển thị ảnh bằng chứng giao hàng (đang làm dở, có thể chưa có trên nhánh của tôi; nếu có thì KHÔNG được xóa hay ghi đè):** cho người gửi và Manager xem ảnh và chữ ký lúc giao. Gồm fragment `deliveryProof` trong `fragments/components.html`, khối trong `user/order-detail.html` và `logistics/complaint.html`, link "Ảnh giao · Chữ ký" trong `manager/orders.html`, `DeliveryProofService.findViewableByOrder(...)` và `orderIdsWithProof(...)`, `DeliveryProofRepository.findProofsByTrackingCodes(...)`.

## A5. Danh sách chức năng giảng viên đã duyệt (BẮT BUỘC giữ nguyên hoạt động)

Đề tài 10, các role Guest, User, Shipper, Manager, Admin. Thầy đã đồng ý danh sách dưới đây qua email. **Mọi thay đổi chỉ được mở rộng, không được làm mất hoặc làm hỏng mục nào.** Mục nào bị ảnh hưởng thì phải cập nhật cho chạy đúng với mô hình chi nhánh.

- **Guest:** trang chủ giới thiệu dịch vụ và số liệu tổng quan; tra cứu vận đơn theo mã, xem hành trình; ước tính cước theo dịch vụ, khối lượng, **khoảng cách**.
- **User:** trang tổng quan cá nhân; tạo đơn (chọn dịch vụ Tiêu chuẩn/Nhanh/Hỏa tốc, tự tính phí, áp mã giảm giá); giỏ đơn nháp lưu DB, xác nhận một hoặc nhiều đơn; lịch sử đơn lọc theo trạng thái, tìm kiếm, phân trang; chi tiết đơn (hành trình, mã QR, ảnh bằng chứng giao hàng); hủy đơn khi chưa lấy hàng; thanh toán COD hoặc ví điện tử; đánh giá dịch vụ sau khi giao; khiếu nại kèm ảnh; sổ địa chỉ (thêm, sửa, xóa, đặt mặc định); hồ sơ cá nhân, đổi mật khẩu, đổi email có OTP; nhận email khi đơn đổi trạng thái.
- **Shipper:** danh sách đơn được phân công, việc trong ngày, đơn hẹn giao lại; lộ trình giao trong ngày; cập nhật trạng thái từng đơn hoặc nhiều đơn; ghi nhận lấy hàng thất bại; chụp ảnh bằng chứng/chữ ký khi giao; quét QR vận đơn; nhận thông báo realtime khi được phân công; đối soát tiền COD; thống kê đơn đã giao, xuất CSV.
- **Manager:** tổng quan vận hành có biểu đồ; tra soát đơn (lọc mã, trạng thái, ngày), tra cứu hàng loạt, xuất CSV; phân công Shipper; cảnh báo đơn quá hạn, chưa phân công, giao thất bại nhiều lần; xử lý giao lại và hoàn hàng; tiếp nhận và phản hồi khiếu nại; đối soát COD; quản lý mã khuyến mãi (tạo, sửa, bật/tắt); xem bảng cước và lịch cước; báo cáo doanh thu, đơn hàng, xuất CSV; xem hiệu suất Shipper.
- **Admin:** **có toàn bộ chức năng của Manager**; quản lý tài khoản 4 role (thêm, sửa, xóa, khóa, cấp lại mật khẩu); cấp tài khoản nội bộ cho Manager; cấu hình bảng cước, lập và hủy lịch thay đổi cước; cấu hình chính sách vận hành; duyệt các hồ sơ liên quan đến tiền và giao lại vượt giới hạn; xem nhật ký thao tác quản trị; quản lý quyền truy cập API (JWT).
- **Chức năng nâng cao:** WebSocket thông báo realtime cho Shipper; JWT cho API nội bộ; mã giảm giá; mã QR vận đơn; **tính phí theo khoảng cách**; email thông báo trạng thái đơn.
- **Chức năng chung theo đề:** đăng ký tài khoản có OTP qua email, đăng nhập, đăng xuất, quên mật khẩu có OTP, mật khẩu mã hóa bằng Spring Security.

Sau mỗi bước, ngoài test của phần mới, hãy nhắc tôi chạy lại toàn bộ `mvn test` để chắc không làm hỏng chức năng cũ.

## A6. Lệnh build và chạy trên máy tôi (Windows PowerShell)

Ví dụ trên máy có JAVA_HOME mặc định trỏ Java 8 (đổi đường dẫn thư mục và JDK theo máy thật):

```powershell
cd D:\WEB\Project_CK
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn -q -B -Pmomo-simulator "-Dmaven.test.skip=true" package   # cần cho test AivenMomoPackagedSmokeIntegrationTest
mvn -q -B test
mvn spring-boot:run                                            # web ở http://localhost:8080
```

`mvn spring-boot:run` đọc template từ `target/classes`, nên sửa HTML/CSS/JS xong phải khởi động lại app. Hiện có 2 test lỗi **có sẵn, không do ta**: `AivenOperationsIntegrationTest.realQueriesAssignmentTariffAndAuditUseSharedSchema` và `AivenControlsIntegrationTest.returnWorkflowLocksRealRowsAndPreservesHistory`. Không tính 2 test này là lỗi do mình gây ra, nhưng cũng không được làm tăng số test lỗi.

Tài khoản test: `thotest`/`shipper1`/`shipper2`/`shipper3` mật khẩu `123456`. `manager1` và `admin` đăng nhập ở `/noi-bo/dang-nhap`, mật khẩu tôi sẽ đưa khi cần.

---

# PHẦN B. CÁC QUYẾT ĐỊNH ĐÃ CHỐT (làm đúng như sau)

## B1. Quy mô: liên tỉnh 3 chi nhánh, mô hình trục và nan (hub-and-spoke)

- 3 chi nhánh, mỗi chi nhánh vừa là bưu cục vừa là kho: **SGN** (TP.HCM), **HAN** (Hà Nội), **DAD** (Đà Nẵng).
- Chỉ nhận lấy và giao trong 3 tỉnh/thành này. Địa chỉ ngoài vùng bị từ chối khi tạo đơn với thông báo "UTEExpress chưa phục vụ khu vực này".
- **Shipper chỉ chạy trong tỉnh của chi nhánh mình, không bao giờ chạy liên tỉnh.** Hàng liên tỉnh đi bằng **chuyến trung chuyển** (xe tải) giữa hai kho.
- **Đơn nội tỉnh:** một shipper chi nhánh đó lấy và giao trọn, **phân công 1 lần** (như hiện tại).
- **Đơn liên tỉnh:** **phân công 2 lần**:
  1. Manager chi nhánh gửi phân công **shipper chi nhánh gửi** đi lấy hàng.
  2. Shipper đó mang hàng về, quét **bàn giao vào kho gửi**, hết trách nhiệm.
  3. Manager kho gửi gom đơn vào chuyến, **xuất kho**.
  4. Manager kho nhận **nhận chuyến**, đối chiếu niêm phong, quét từng kiện.
  5. Manager chi nhánh nhận phân công **shipper chi nhánh nhận** đi giao.

## B2. Nhân sự và tài khoản

- Chốt bổ sung 23/10/2026: giữ cố định ba mã SGN/HAN/DAD và tỉnh phục vụ. Admin sửa tên, địa chỉ kho, khu vực kho thuộc chính chi nhánh và bật/tắt nhận đơn mới; không thêm/xóa chi nhánh trên web.

- **1 Admin** toàn hệ thống. **Mỗi chi nhánh 1 Manager** (tổng 3): `manager1` gán SGN, tạo thêm `manager_han`, `manager_dad`. Lý do: nhận chuyến là bước kiểm soát của kho đến, một người không thể vừa xuất vừa nhận.
- **Mỗi chi nhánh 1–2 shipper**: SGN có `shipper1`, `shipper2`; HAN có `shipper3`, `shipper_han2`; DAD có `shipper_dad1`.
- **Không có trang tự đăng ký cho shipper hay Manager.** Nhân sự ký hợp đồng với chi nhánh ngoài hệ thống, sau đó **Admin cấp tài khoản** ở `/admin/quan-ly-tai-khoan` và **bắt buộc chọn chi nhánh** cho SHIPPER/MANAGER. Shipper thuộc chi nhánh nào thì chỉ nhận việc ở chi nhánh đó. Đổi chi nhánh chỉ khi shipper không còn giữ đơn đang chạy.
- **Không tạo role tài xế xe tải.** Tài xế là **thông tin của chuyến trung chuyển** (biển số, tên, SĐT), kiểm soát hàng bằng **mã niêm phong + quét kiện ở hai đầu kho**.
- **Manager hai chi nhánh của một đơn liên tỉnh đều xem được đơn đó**, nhưng **chỉ người phụ trách đúng giai đoạn mới có nút thao tác** (server cũng chặn):
  - Manager chi nhánh gửi thao tác từ chờ lấy tới xuất chuyến.
  - Manager chi nhánh nhận thao tác từ nhận chuyến tới giao xong.
  - Manager chi nhánh thứ ba không thấy đơn.

## B3. Cách lưu trạng thái: giữ enum cũ, thêm giai đoạn tuyến

**Không thêm giá trị vào `Order.OrderStatus`.** Thêm bảng `order_route` với cột `stage`:

| `OrderStatus` (giữ nguyên) | `order_route.stage` | Nghĩa |
|---|---|---|
| PENDING_PICKUP | PICKUP | Chờ shipper chi nhánh gửi lấy |
| PICKED_UP | ORIGIN_HUB | Đã nhập kho gửi |
| PICKED_UP | LINEHAUL | Đang trung chuyển |
| PICKED_UP | DEST_HUB | Đã nhập kho nhận, chờ phân công giao |
| IN_TRANSIT | LAST_MILE | Shipper chi nhánh nhận đang giao |
| DELIVERED / FAILED / RETURNED | giữ stage cuối | Kết thúc như cũ |

Với đơn nội tỉnh, `stage` đi thẳng `PICKUP → LAST_MILE` và code cũ vẫn chạy đúng. `Order.shipper` mang nghĩa "shipper đang chịu trách nhiệm hiện tại"; ai làm chặng nào thì ghi ở `order_route.pickup_shipper_id` / `delivery_shipper_id`.

## B4. Bảng mới (tất cả là bảng riêng, không sửa `shipment_order`/`app_user`)

**Danh mục đã chốt:** dùng khái niệm nội bộ **khu vực giao nhận (area)**, đặt tên theo địa bàn quận/huyện cũ; không gọi là cấp quận/huyện hành chính hiện hành. SGN có 22 khu vực, HAN 30, DAD 7 (bỏ Hoàng Sa), tổng 59. Mã dạng `SGN-THUDUC`, `HAN-CAUGIAY`, `DAD-HAICHAU`. Tọa độ tâm xấp xỉ, dùng cho sắp lộ trình; lân cận lập tay theo ranh giới cũ, hai chiều, không khác chi nhánh. Test bảo đảm mã duy nhất, mỗi khu vực thuộc đúng một chi nhánh và lân cận đối xứng.

| Bảng | Cột |
|---|---|
| `branch` | id, code (SGN/HAN/DAD, unique), name, province_code, hub_address, hub_area_code, active, revision, created_at, updated_at. Tâm kho SGN=SGN-THUDUC, HAN=HAN-CAUGIAY, DAD=DAD-HAICHAU |
| `province_area` | id, province_code, area_code (unique), area_name, branch_id, lat, lng. Danh mục nội bộ nạp bằng migration |
| `area_neighbor` | area_code, neighbor_code (khóa chính ghép). Khu vực lân cận hai chiều |
| `staff_branch` | user_id (unique), branch_id, assigned_by, assigned_at. Gán SHIPPER/MANAGER vào chi nhánh |
| `shipper_zone` | shipper_id, area_code, is_primary. Một khu vực chính + các khu vực phụ, **cùng chi nhánh** |
| `order_route` | order_id (unique), origin_branch_id, dest_branch_id, pickup_area_code, delivery_area_code, stage, pickup_shipper_id, delivery_shipper_id, revision; pickup_mode bổ sung riêng sau bước 9 |
| `linehaul_trip` | id, code, from_branch_id, to_branch_id, status (LOADING/DEPARTED/ARRIVED), vehicle_plate, driver_name, driver_phone, seal_code, item_count, departed_by, departed_at, arrived_by, arrived_at, arrival_note, revision |
| `linehaul_item` | trip_id, order_id, received (true/false) |
| `route_tariff` | origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision |
| `saved_address_area` | Một dòng/địa chỉ, province_code và area_code |
| `draft_order_area` | Một dòng/đơn nháp, hai cặp tỉnh/khu vực gửi–nhận |

Thêm `operations_policy.max_active_orders INT NOT NULL DEFAULT 15`. Sau bước 9 thêm `drop_off_discount DECIMAL(12,0) NOT NULL DEFAULT 3000` bằng migration riêng. Bốn FK cấu hình/phụ xóa kèm: `fk_saved_area_address`, `fk_draft_area_order`, `fk_staff_branch_user`, `fk_zone_shipper`; giữ RESTRICT cho quan hệ lịch sử và người thao tác.

## B5. Thuật toán gán đơn cho shipper (trang Phân công)

**Ràng buộc cứng, server kiểm tra.** Thiếu một điều kiện là báo lỗi rõ ràng, không âm thầm gán cho người khác:
1. Shipper đang hoạt động, không bị khóa, đúng vai trò SHIPPER (đã có).
2. Đúng chi nhánh của chặng:
   - `stage = PICKUP` thì `staff_branch(shipper) = origin_branch`.
   - `stage = DEST_HUB` hoặc đơn nội tỉnh đến chặng giao thì `staff_branch(shipper) = dest_branch`.
3. Manager thao tác thuộc đúng chi nhánh đó. Admin thì được mọi chi nhánh.
4. Số đơn shipper đang giữ (đang ở `PENDING_PICKUP`, `PICKED_UP` mà shipper còn giữ, hoặc `IN_TRANSIT`) **nhỏ hơn `maxActiveOrders`**.
5. Đơn đủ điều kiện thanh toán (đã có: `payments.requireReadyForFulfillment`).

**Xếp hạng gợi ý** trong số shipper hợp lệ:
1. Quận cần đến (quận lấy ở chặng đầu, quận giao ở chặng cuối) nằm trong `shipper_zone` **quận chính** của shipper.
2. Nằm trong quận phụ của shipper.
3. Nằm trong khu vực lân cận (`district_neighbor`) của khu vực shipper.
4. Còn lại.
5. Cùng mức ở trên thì ưu tiên shipper **ít đơn đang giữ hơn**, rồi **ít đơn được giao trong hôm nay hơn** (công bằng), cuối cùng **id nhỏ hơn** (để kết quả lặp lại được khi test).

**Giao diện trang Phân công:**
- Chỉ hiện đơn và shipper **thuộc chi nhánh của Manager** (Admin có ô lọc chi nhánh).
- Hiện địa chỉ có quận của đơn. Cạnh tên mỗi shipper hiện "đang giữ X/15 đơn" và khu vực phụ trách.
- Shipper xếp hạng 1 được **chọn sẵn**. Ô lý do tự điền gợi ý, ví dụ "Gợi ý hệ thống: phụ trách quận Bình Thạnh, ít việc nhất". Manager sửa được rồi bấm xác nhận, vẫn có nhật ký.
- Nút **"Phân công tất cả theo gợi ý"**: duyệt đơn chờ của chi nhánh theo thứ tự cũ trước. Mỗi lần gán thì cộng tải cho người vừa nhận rồi mới xét đơn sau. Đơn không còn ai hợp lệ thì giữ lại và báo "Hết shipper còn sức chứa".
- **Gộp hai service phân công thành một** (giữ logic an toàn của `DispatchService`). Sửa test liên quan cho khớp.

## B6. Sắp xếp thứ tự đi cho shipper (trang "Lộ trình hôm nay" `/shipper/lo-trinh`)

1. Chia nhóm theo độ gấp:
   1. Hỏa tốc, và đơn có giờ hẹn sắp tới (giao lại đến hẹn).
   2. Đơn sắp quá hạn theo Chính sách vận hành.
   3. Còn lại.
2. Trong từng nhóm, xếp theo **láng giềng gần nhất**: xuất phát từ **kho chi nhánh**, đi tới điểm gần nhất, rồi từ đó tới điểm gần nhất tiếp theo... Khoảng cách tính bằng **công thức Haversine giữa tọa độ trung tâm quận** (`province_area.lat/lng`). **Không gọi Google Maps hay API trả phí nào.**
3. Đơn nội tỉnh shipper vừa lấy vừa giao: điểm lấy luôn đứng trước điểm giao của cùng đơn.

**Không dùng địa chỉ nhà riêng của shipper để phân đơn hay sắp lộ trình**: shipper xuất phát từ kho, địa chỉ nhà là dữ liệu cá nhân nhạy cảm, không phản ánh vị trí làm việc. Muốn vị trí thật thì cần GPS, vượt phạm vi đồ án. Địa chỉ liên hệ vẫn lưu trong hồ sơ, chỉ không đưa vào thuật toán.

## B7. Cước theo khoảng cách và tuyến (một hàm tính duy nhất)

**"Tính phí theo khoảng cách" nằm trong danh sách chức năng giảng viên đã duyệt, nên phải giữ.** Chỉ đổi cách lấy khoảng cách và áp đúng phạm vi:

```
Cước = baseFee(dịch vụ) + feePerKg × kg
     + Phụ phí khoảng cách   (chỉ đơn NỘI TỈNH)
     + Phụ phí tuyến          (chỉ đơn LIÊN TỈNH, route_tariff theo cặp chi nhánh + dịch vụ)
```

- **Phụ phí khoảng cách (nội tỉnh):** khoảng cách tính **tự động** bằng công thức Haversine giữa tọa độ trung tâm quận lấy hàng và quận giao hàng (`province_area.lat/lng`), người dùng không tự nhập km. Áp lại bậc phụ phí đang có trong `FeeEstimateService` (0–5 km miễn phí, 5–30 km 1.000 đ/km, trên 30 km theo bậc hiện có) và hệ số ×1 / ×1,3 / ×1,6 theo dịch vụ, làm tròn đến 100 đ. Hỏa tốc chỉ nhận khi khoảng cách ≤ 30 km.
- **Đơn liên tỉnh không cộng phụ phí km** (tránh ra 246.000 đ cho TP.HCM–Hà Nội), mà cộng phụ phí tuyến theo bảng dưới.

| Tuyến | Tiêu chuẩn | Nhanh | Hỏa tốc | Thời gian (Tiêu chuẩn / Nhanh) |
|---|---|---|---|---|
| Nội tỉnh | 0 | 0 | 0 | 1–2 ngày / trong ngày |
| TP.HCM ↔ Đà Nẵng | 15.000 | 25.000 | **không nhận** | 3–4 ngày / 2 ngày |
| Hà Nội ↔ Đà Nẵng | 15.000 | 25.000 | **không nhận** | 3–4 ngày / 2 ngày |
| TP.HCM ↔ Hà Nội | 25.000 | 40.000 | **không nhận** | 4–5 ngày / 2–3 ngày |

- Tạo hàm dùng chung kiểu `quote(ServiceType, kg, pickupArea, deliveryArea)` (từ quận suy ra chi nhánh và khoảng cách). **Trang Ước tính cước và lúc tạo đơn phải gọi cùng hàm này**, để hai nơi luôn ra cùng một giá. Kết quả trả về tách từng phần: cước gốc, phụ phí khoảng cách (kèm số km), phụ phí tuyến, tổng.
- Trang Ước tính cước đổi ô nhập "khoảng cách km" thành chọn **Tỉnh + Khu vực gửi** và **Tỉnh + Khu vực nhận**. Trang hiện số km hệ thống tự tính.
- Hỏa tốc chỉ cho phép khi cùng chi nhánh **và** khoảng cách ≤ 30 km, cả ở server lẫn giao diện.
- Admin cấu hình `route_tariff` ở trang cấu hình cước. Có validation không âm, có revision, có nhật ký.
- Mã giảm giá vẫn trừ vào cước như cũ.

## B8. Phân quyền lập/duyệt hồ sơ vận hành

| Loại | Ai lập | Ai duyệt |
|---|---|---|
| Giao lại, trong giới hạn `maxDeliveryAttempts` | Manager hoặc Admin | **Không cần duyệt: lập là có hiệu lực** |
| Giao lại, đã vượt giới hạn | Manager | **Admin** |
| Chuyển hoàn | Manager hoặc Admin | **Không cần duyệt: lập là có hiệu lực** |
| Nộp bổ sung cước thiếu | **Chỉ Manager** | **Admin** |
| Hoàn cước nộp thừa | **Chỉ Manager** | **Admin** |
| Bồi hoàn cước | **Chỉ Manager** | **Admin** |

- Danh sách đã duyệt ghi **"Admin có toàn bộ chức năng của Manager"**, nên Admin vẫn làm được mọi việc của Manager (phân công, kho, khiếu nại, giao lại, hoàn hàng...). **Ngoại lệ duy nhất: Admin không lập 3 loại phiếu tiền**, vì Admin là người duyệt chi tiền (nguyên tắc hai người). Server chặn Admin gọi `request(...)` với 3 loại này. Ẩn nút "Đề xuất bồi hoàn" và form lập phiếu tiền đối với Admin, thay bằng ghi chú "Phiếu tiền do Manager lập, Admin duyệt".
- Giữ kiểm tra "người duyệt ≠ người lập" cho phiếu tiền, làm lớp an toàn thứ hai.
- Ghi nhận chi trả (`complete`) cho phiếu tiền: Admin, khác người lập (giữ như cũ).
- Với mô hình chi nhánh: Manager chỉ lập hồ sơ cho đơn hoặc phiếu thuộc chi nhánh mình.

**Ô lọc loại hồ sơ** đổi sang `<optgroup>` 3 nhóm, đồng thời đổi nhãn hiển thị cho đỡ nhầm (đổi chữ hiển thị trong enum `OperationsCase.Kind`, không đổi tên hằng):
- **Xử lý hàng:** "Giao lại", "Hoàn hàng về người gửi" (trước là "Chuyển hoàn")
- **Đối soát COD với Shipper:** "Shipper nộp bù tiền thiếu", "Trả lại tiền Shipper nộp thừa"
- **Chi trả cho khách:** "Bồi hoàn cước"

Thêm ô lọc **trạng thái**, "Chờ duyệt" đứng đầu. Với Admin, mặc định mở sẵn "Chờ duyệt".

## B9. Hoàn hàng liên tỉnh (làm sau cùng, nếu kịp)

Đơn liên tỉnh giao thất bại vượt giới hạn → Manager chi nhánh nhận lập "Hoàn hàng về người gửi" → đơn đi **chuyến trung chuyển ngược** về kho gửi → shipper chi nhánh gửi trả hàng → `RETURNED`. Dùng lại màn hình kho với chiều ngược lại, không thêm bảng.

## B10. Xử lý dữ liệu cũ

Công cụ `--check` in bảng ánh xạ cho tôi duyệt trước, rồi mới `--apply`:
- Địa chỉ chứa "TP.HCM / Hồ Chí Minh / Thủ Đức / Quận x" gán SGN, "Hà Nội" gán HAN, "Đà Nẵng" gán DAD.
- Đơn ngoài 3 tỉnh mà **đã kết thúc**: giữ nguyên, ghi chi nhánh gần nhất để báo cáo không lỗi. Đơn **đang chạy** ngoài vùng thì liệt kê ra để Manager xử lý tay.
- Gán `shipper1`, `shipper2`, `manager1` vào SGN; `shipper3` vào HAN. Tạo `manager_han`, `manager_dad`, `shipper_han2`, `shipper_dad1`, mỗi người kèm khu vực phụ trách.

---

## B11. Tự mang ra chi nhánh (chốt bổ sung 23/10/2026, triển khai sau bước 9)

1. **Migration riêng `UpgradeAivenDropOff`:** thêm `order_route.pickup_mode ENUM('COURIER','DROP_OFF') NOT NULL DEFAULT 'COURIER'` và `operations_policy.drop_off_discount DECIMAL(12,0) NOT NULL DEFAULT 3000`. Có `--print-schema`, `--check`, `--apply`, chạy từng câu với timeout riêng; chỉ chạy check rồi dừng chờ chủ DB apply. Không sửa `Order.java`.
2. **Tạo đơn:** radio “Shipper đến lấy” (mặc định) / “Tự mang ra chi nhánh”. Vẫn bắt nhập địa chỉ/khu vực người gửi để xác định chi nhánh và hoàn hàng. Chi nhánh nhận tại quầy là chi nhánh tỉnh gửi, hiển thị tên/địa chỉ kho khi chọn; không được chọn khác tỉnh.
3. **Cước:** hàm quote chung thêm `pickupMode`. DROP_OFF giảm `drop_off_discount` (mặc định 3.000 đ), tổng không âm, sau đó áp mã giảm giá theo luồng cũ. Trang ước tính có lựa chọn và dòng “Giảm do tự mang ra chi nhánh”. Admin sửa giảm giá 0–20.000 đ ở Chính sách vận hành, kiểm revision và ghi nhật ký.
4. **Nhận tại quầy:** đơn tạo vẫn `PENDING_PICKUP` / `PICKUP`; không nằm trong hàng chờ phân công lấy và không tính cảnh báo “chưa phân công”. Kho gửi có danh sách “Chờ khách mang tới”, nút “Nhận hàng tại quầy” nhập/quét vận đơn. Server kiểm Manager đúng kho gửi (Admin mọi kho), DROP_OFF, PENDING_PICKUP, `payments.requireReadyForFulfillment`. Liên tỉnh chuyển `PICKED_UP` / `ORIGIN_HUB`; nội tỉnh chuyển `PICKED_UP` / `DEST_HUB`, chờ phân công giao, không qua chuyến xe. Ghi lịch sử “Nhận hàng tại quầy chi nhánh SGN”, nhật ký và email người gửi.
5. **Hủy/quá hạn:** người gửi vẫn hủy khi PENDING_PICKUP. Quá `pickupHours` chưa mang đến thì cảnh báo “Khách chưa mang hàng tới chi nhánh”, không tự hủy.
6. **Hành trình:** mốc đầu “Chờ bạn mang hàng tới chi nhánh SGN (địa chỉ...)”; sau nhận là “Đã nhận tại quầy chi nhánh SGN”. Áp dụng người gửi và Guest.
7. **Test tối thiểu:** không vào phân công lấy; Manager khác kho bị chặn; nhận nội tỉnh→DEST_HUB, liên tỉnh→ORIGIN_HUB; giá ước tính và tạo đơn cùng chế độ bằng nhau, giảm đúng mức.

# PHẦN C. CÁC BƯỚC LÀM (bạn làm hết, theo đúng thứ tự)

| Bước | Việc |
|---|---|
| 1 | **Migration `UpgradeAivenBranches`:** tạo các bảng ở B4, thêm cột `maxActiveOrders` vào `OperationsPolicy`, nạp 3 chi nhánh, danh mục khu vực giao nhận 3 tỉnh kèm tọa độ và khu vực lân cận, `route_tariff` mặc định (B7). Tạo entity và repository tương ứng, thêm vào `AivenSchema` |
| 2 | **Admin quản lý chi nhánh và nhân sự:** giữ ba mã, sửa thông tin/khu vực kho và bật/tắt nhận đơn (B2). Trang Quản lý tài khoản có chi nhánh bắt buộc cho SHIPPER/MANAGER, khu vực chính/phụ cùng chi nhánh. Không đổi chi nhánh khi Shipper còn đơn đang chạy |
| 3 | **Hàm cước dùng chung** `quote(ServiceType, kg, pickupArea, deliveryArea)` theo B7 (phụ phí khoảng cách cho nội tỉnh, phụ phí tuyến cho liên tỉnh) và trang Admin cấu hình `route_tariff`. Hỏa tốc chỉ cùng chi nhánh và ≤ 30 km |
| 4 | **Tạo đơn có địa chỉ cấu trúc:** form tạo đơn chọn Tỉnh → Khu vực cho địa chỉ lấy và giao (kèm ô số nhà, đường). Server kiểm tra khu vực thuộc tỉnh, tỉnh được phục vụ. Tính cước bằng hàm ở bước 3. Tạo `order_route` (stage = PICKUP) **trong cùng transaction** tạo đơn |
| 5 | **Sổ địa chỉ và giỏ đơn** lưu tỉnh/khu vực (bảng phụ). Địa chỉ cũ thiếu tỉnh/khu vực thì bắt chọn lại khi dùng |
| 6 | **Trang Ước tính cước theo tuyến:** chọn Tỉnh + Khu vực gửi và nhận, gọi hàm cước chung (B7), hiện số km tự tính và từng khoản phí, ẩn Hỏa tốc khi khác tỉnh hoặc quá 30 km |
| 7 | **Phân công theo B5:** gộp 2 service phân công thành một, ràng buộc cứng, xếp hạng gợi ý, hiện tải việc và khu vực phụ trách, chọn sẵn người đứng đầu, nút phân công hàng loạt |
| 8 | **Shipper chỉ thấy việc đúng chặng** của mình, hiện tên chi nhánh. Chặng lấy hàng liên tỉnh: sau "Đã lấy hàng" có nút **"Bàn giao vào kho"** (quét QR) → `stage = ORIGIN_HUB`, shipper hết giữ đơn. Chặng giao: nhận đơn ở `DEST_HUB`, đi tiếp như hiện tại |
| 9 | **Màn hình kho cho Manager:** nhập kho, tạo chuyến (biển số, tài xế, SĐT), xuất kho có mã niêm phong và số kiện, nhận chuyến (đối chiếu niêm phong, quét từng kiện, đánh dấu thiếu và lập hồ sơ). Mỗi thao tác ghi `OrderStatusHistory` và nhật ký. Chỉ Manager đúng chi nhánh, đúng giai đoạn mới thao tác được |
| 9b | **Tự mang ra chi nhánh theo B11:** migration `UpgradeAivenDropOff` và dừng chờ apply, rồi nối radio, quote, chính sách giảm giá, nhận tại quầy, hành trình, cảnh báo và test |
| 10 | **Manager chỉ thấy dữ liệu chi nhánh mình:** tra soát, cảnh báo, khiếu nại, đối soát COD, báo cáo, hồ sơ vận hành. Đơn liên tỉnh thì Manager hai đầu đều xem được, nhưng chỉ người đúng giai đoạn có nút. Admin xem tất cả, có ô lọc chi nhánh |
| 11 | **Phân quyền hồ sơ vận hành theo B8,** ô lọc 3 nhóm, đổi nhãn hiển thị, thêm ô lọc trạng thái |
| 12 | **Hành trình cho người gửi và Guest** có các mốc kho ("Nhập kho SGN", "Đang trung chuyển SGN → HAN", "Nhập kho HAN"), hiện chi nhánh gửi/nhận và thời gian dự kiến. Trợ lý Groq và email cập nhật đơn có thông tin kho hiện tại |
| 13 | **COD do shipper giao hàng thu,** phiếu nộp tiền thuộc chi nhánh nhận. Thống kê shipper và CSV tách đơn đã lấy, đơn đã giao |
| 14 | **Lộ trình hôm nay theo B6.** Giao thất bại liên tỉnh thì đơn về `DEST_HUB` |
| 15 | **Báo cáo và hiệu suất shipper theo chi nhánh** |
| 16 | **Chuyển dữ liệu cũ** (B10), tạo các tài khoản demo ở B2 kèm chi nhánh và khu vực phụ trách |
| 17 | (Tùy chọn, làm sau cùng) **Hoàn hàng liên tỉnh** theo B9 |

Bước 1–7 là phần **sửa đúng lỗi phân công sai tỉnh**: làm xong 7 bước này thì shipper sai chi nhánh đã không thể nhận đơn. Các bước sau hoàn thiện luồng liên tỉnh.

---
# PHẦN D. CÁCH BẠN LÀM VIỆC VỚI TÔI

1. **Trước khi viết code:** nếu bạn truy cập được repo thì tự đọc các file liên quan. Nếu không, **liệt kê cho tôi các file bạn cần xem** (tối thiểu: `entity/Order.java`, `entity/AppUser.java`, `service/tracking/DispatchService.java`, `controller/manager/DispatchController.java`, `templates/manager/dispatch.html`, `service/tracking/impl/TrackingServiceImpl.java`, `service/manager/impl/OperationsCaseServiceImpl.java`, `service/admin/StaffAccessPolicy.java`, `service/admin/impl/ShippingTariffServiceImpl.java`, `service/FeeEstimateService.java`, `service/impl/OrderServiceImpl.java`, `entity/admin/OperationsPolicy.java`, một file `tool/database/UpgradeAiven*.java` mẫu, `tool/database/AivenSchema.java`) và chờ tôi gửi. **Không đoán tên field, method hay route.**
2. **Làm lần lượt từng bước ở phần C, mỗi lần một bước.** Xong một bước thì đưa đầy đủ code và hướng dẫn kiểm tra, rồi chờ tôi báo kết quả. Tôi gõ **"tiếp"** thì bạn làm bước sau; tôi báo lỗi thì bạn sửa xong mới đi tiếp. Không gộp nhiều bước vào một lần trả lời, vì code dài sẽ bị cắt.
3. Mỗi lần báo cáo gồm: danh sách file thêm/sửa, **code đầy đủ** của phần thay đổi (không dùng "..." thay cho code), DDL migration nếu có (kèm lệnh `--print-schema`, `--check`, `--apply`), test JUnit mới hoặc đã sửa, các bước tôi kiểm tra trên trình duyệt kèm tài khoản dùng, và rủi ro hoặc điểm còn mở.
4. **Mọi kiểm tra quyền và ràng buộc phải ở server.** Ẩn nút chỉ là phụ.
5. Viết test cho mỗi ràng buộc quan trọng, tối thiểu:
   - Gán shipper SGN cho chặng giao ở HAN thì bị từ chối.
   - Shipper đủ 15 đơn thì không còn trong gợi ý và không gán được.
   - Manager HAN không xem được đơn nội tỉnh SGN.
   - Admin lập phiếu tiền thì bị từ chối.
   - Ước tính cước và tạo đơn cùng tuyến cùng dịch vụ ra cùng một giá.
   - Hỏa tốc khác tỉnh bị từ chối.
   - Thuật toán xếp hạng trả đúng thứ tự trên dữ liệu cố định.
6. **Không sửa** `Order.java`, `AppUser.java`, hay giá trị enum `OrderStatus`. Thấy bắt buộc phải sửa thì dừng lại hỏi tôi kèm lý do.
7. **Không xóa hay ghi đè** chức năng ảnh bằng chứng giao hàng liệt kê ở A4 nếu repo đã có.
8. **Không đụng file bí mật.** Cần cấu hình mới thì tạo file `.example` và hướng dẫn tôi tự điền.
9. Gặp chỗ mâu thuẫn giữa tài liệu này và code thật thì **báo tôi trước**, không tự chọn.

# KỊCH BẢN NGHIỆM THU CUỐI

1. **Nội tỉnh SGN → SGN:** Manager SGN thấy gợi ý đúng shipper SGN phụ trách quận đó và ít việc nhất. Shipper đó lấy và giao trọn. Manager HAN và DAD **không thấy** đơn.
2. **Liên tỉnh SGN → HAN:** shipper SGN lấy hàng, bàn giao kho SGN. Manager SGN tạo chuyến (biển số, tài xế, niêm phong), xuất kho. Manager HAN nhận chuyến, quét đủ kiện. Phân công shipper HAN giao. Người gửi thấy đủ các mốc. Cước có phụ phí tuyến và khớp trang ước tính. Manager SGN và HAN đều xem được đơn, nhưng mỗi người chỉ có nút ở giai đoạn của mình.
3. **Chặn sai chi nhánh:** sửa request bằng tay để gán shipper SGN cho chặng giao ở HAN thì server từ chối.
4. **Sức chứa:** shipper đủ 15 đơn thì không còn trong gợi ý. Phân công hàng loạt bỏ qua người đó.
5. **Ngoài vùng:** tạo đơn giao Cần Thơ thì bị từ chối với thông báo rõ ràng.
6. **Hồ sơ:** `manager1` lập Bồi hoàn cước, `admin` duyệt được. `admin` không lập được phiếu tiền nhưng vẫn lập được Giao lại/Hoàn hàng. `manager1` lập Giao lại trong giới hạn thì có hiệu lực ngay, không cần duyệt.

Hãy bắt đầu bằng việc **xác nhận lại bạn đã hiểu** (tóm tắt ngắn 5–10 gạch đầu dòng), **liệt kê các file cần xem** cho **bước 1 (migration)**, và nêu câu hỏi nếu có. Chưa viết code ở lượt trả lời này.
