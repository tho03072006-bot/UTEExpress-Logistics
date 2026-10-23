# BÀN GIAO ĐỢT 2: KẾ HOẠCH LIÊN TỈNH 3 CHI NHÁNH (SGN/HAN/DAD)

Nhánh làm việc: **`feature_Tho_lien_tinh`** (GitHub `tho03072006-bot/UTEExpress-Logistics`). Commit mới nhất khi bàn giao: xem `git log -1`.
Tài liệu này nối tiếp `docs/PROMPT_BAN_GIAO_PHAN_CON_LAI.md` (quy tắc, bẫy đã gặp) và `docs/PROMPT_CHATGPT_LIEN_TINH.md`
(quyết định B1–B11, kịch bản nghiệm thu). **Quy tắc ở mục 1 của tài liệu bàn giao cũ vẫn giữ nguyên** (không sửa `Order.java`,
`AppUser.java`, enum `OrderStatus`; Aiven chỉ ghi bằng công cụ migration theo `--check` → `--apply` → `--check`; không đụng file bí mật;
không ghi tên công cụ AI vào code/commit; màu chỉ xanh `#0072BC`, trắng, xám; giao diện tiếng Việt có dấu).

---

## 1. Trạng thái: bước 1–17 đã có code và test

| Bước | Nội dung | Ghi chú |
|---|---|---|
| 1–11 | Chi nhánh, khu vực, cước theo tuyến, phân công B5, kho, tự mang ra chi nhánh, phạm vi chi nhánh, phân quyền hồ sơ B8 | Đợt 1 |
| 12 | Trợ lý Groq trả tuyến, vị trí kho, thời gian dự kiến; email cập nhật đơn có dòng "Vị trí hiện tại" | `AssistantToolServiceImpl`, `SenderOrderMailListener`, `OrderRoutingService.describe` |
| 13 | Thống kê Shipper và hiệu suất có cột "Đã lấy" (theo `order_route.pickup_shipper_id`); đơn đã bàn giao kho không còn tính "Đang xử lý" | `ShipperStatisticsServiceImpl`, `OperationsOverviewRepository.performance` |
| 14 | Lộ trình hôm nay: nhóm độ gấp → láng giềng gần nhất (Haversine tâm khu vực) → xuất phát từ kho chi nhánh | `ShipperRouteService.sequence/urgency`; "sắp quá hạn" = đã dùng từ 75% thời hạn |
| 15 | CSV báo cáo có dòng `Chi_nhanh` ghi tên chi nhánh đã lọc | `ManagerReportController.branchLabel/csvCell` |
| 16a | Công cụ `UpgradeAivenLegacyRoutes` gắn tuyến cho đơn cũ | Đã apply trên Aiven: thêm 60 tuyến, 39 đơn phải xử lý tay (mục 4) |
| 16b | Nhân sự theo chi nhánh | Đã làm trên web (mục 3) |
| 17 | Hoàn hàng liên tỉnh (B9) qua chuyến trung chuyển ngược | Có code và test tích hợp; **chưa nghiệm thu trên web thật** (mục 5.1) |

Bổ sung ngoài kế hoạch, đã sửa: gán chi nhánh lần đầu cho shipper có từ trước (B2 chỉ chặn *đổi* chi nhánh khi còn đơn chạy);
ẩn nút "Đề xuất bồi hoàn" với Admin (B8); test ràng buộc B5 (`DispatchRulesIntegrationTest`).

### Bước 17 hoạt động thế nào
1. Đơn liên tỉnh giao thất bại ở chi nhánh nhận → Manager **chi nhánh nhận** lập "Hoàn hàng về người gửi" (có hiệu lực ngay)
   → bấm "Bắt đầu hoàn": `order_route.return_leg = true`, giai đoạn `DEST_HUB`, shipper giao hết giữ đơn.
2. Trang kho chi nhánh nhận (`/manager/kho`) hiện hàng hoàn (nhãn "Hàng hoàn") trong form tạo chuyến về chi nhánh gửi;
   xếp chuyến, niêm phong, xuất kho như chiều đi. Chi nhánh gửi nhận chuyến → giai đoạn `ORIGIN_HUB` (chiều ngược).
3. Trang kho chi nhánh gửi có mục "Hàng hoàn đã về kho, chờ trả người gửi" → Manager **chi nhánh gửi** hoàn tất hồ sơ hoàn
   (ảnh biên nhận) → `RETURNED`. Mỗi bước chỉ đúng kho (hoặc Admin) thao tác được. Đơn hoàn nội tỉnh giữ luồng cũ.

---

## 2. Migration đã chạy trên Aiven (kết quả `--check` lần cuối)

| Công cụ | Kết quả |
|---|---|
| `UpgradeAivenDropOff` | Schema đã đầy đủ (`order_route.pickup_mode`, `operations_policy.drop_off_discount`) |
| `UpgradeAivenLegacyRoutes` | Dữ liệu cũ đã đầy đủ tuyến (trừ 39 đơn phải xử lý tay) |
| `UpgradeAivenReturnLeg` | Schema đã đầy đủ (`order_route.return_leg BIT NOT NULL DEFAULT 0`) |

Lệnh chạy công cụ: xem mục 1 của `docs/PROMPT_BAN_GIAO_PHAN_CON_LAI.md` (thay tên lớp).

## 3. Tài khoản nội bộ theo chi nhánh (không ghi mật khẩu; chủ repo gửi riêng)

- Có sẵn, đã gán: `manager1` → SGN; `shipper1` → SGN (khu vực chính Thủ Đức); `shipper2` → SGN (Khu vực 1); `shipper3` → HAN (Cầu Giấy).
- Tạo mới: `manager_han` (HAN), `manager_dad` (DAD), `shipper_han2` (HAN, Đống Đa), `shipper_dad1` (DAD, Hải Châu).
- Manager mới bị bắt đổi mật khẩu ở lần đăng nhập đầu: đã đổi xong.

## 4. Đơn cũ phải xử lý tay (39 đơn, vẫn hiện cho mọi chi nhánh theo quy tắc chuyển tiếp)

- Liên tỉnh SGN → HAN đang chạy (4): UTE250011, UTE250035, UTE250051, UTE250075
- Liên tỉnh SGN → DAD đang chạy (6): UTE250010, UTE250018, UTE250034, UTE250050, UTE250058, UTE250074
- Đang chạy ngoài 3 tỉnh (29): UTE01798876, UTE13959611, UTE250008, UTE250012, UTE250014, UTE250015, UTE250028–UTE250032, UTE250038,
  UTE250048, UTE250052–UTE250055, UTE250068–UTE250072, UTE250078, UTE30789197, UTE45963478, UTE75130298, UTE80988321, UTE88515491, UTE92262707

Chạy `UpgradeAivenLegacyRoutes --check` để xem lại bảng ánh xạ chi tiết.

## 5. Việc còn lại cho người tiếp quản

### 5.1. Nghiệm thu bước 17 trên web thật (Aiven)
Tạo một đơn kiểm thử SGN → HAN, đi tới chặng giao ở HAN, shipper HAN báo giao thất bại; `manager_han` lập "Hoàn hàng" và "Bắt đầu hoàn";
`manager_han` xếp chuyến về SGN, niêm phong, xuất kho; `manager1` nhận chuyến; `manager1` hoàn tất hồ sơ hoàn → đơn `RETURNED`.
Kiểm thêm: `manager1` không bắt đầu hoàn được, `manager_han` không hoàn tất được; người gửi thấy các mốc "Hàng hoàn về kho …",
"Hàng hoàn đang trung chuyển", "Hàng hoàn về kho …", "Đã hoàn trả". Ghi mã đơn kiểm thử vào báo cáo.

### 5.2. Kịch bản 4 (sức chứa 15 đơn) mới chỉ có test tự động
`DispatchRulesIntegrationTest` đã chứng minh: đủ số đơn thì không còn trong gợi ý, không gán được, phân công theo gợi ý báo
"Hết shipper còn sức chứa". Muốn kiểm trên web thì phải tạo trên 15 đơn thật cho một shipper trên Aiven dùng chung: hỏi chủ repo trước.

### 5.3. Hai test Aiven lỗi có sẵn (không do kế hoạch này) nên sửa cho đúng nghiệp vụ mới
- `AivenControlsIntegrationTest.returnWorkflowLocksRealRowsAndPreservesHistory`: test vẫn gọi `review(...)` cho hồ sơ hoàn,
  nhưng theo B8 hồ sơ hoàn đã "lập là có hiệu lực" (APPROVED ngay) nên `review` báo "Hồ sơ đã được xử lý". Bỏ bước `review`.
- `AivenOperationsIntegrationTest.realQueriesAssignmentTariffAndAuditUseSharedSchema`: đơn tạo trong test thiếu bản ghi thanh toán
  nên phân công báo "chưa đủ điều kiện thanh toán". Tạo `OrderPayment` COD cho đơn trong test.

### 5.4. Gộp với `develop` (để nhóm quyết khi mở PR, chưa merge)
Gộp thử với `develop` hiện tại thì xung đột 7 file: `UserOrderController`, `FeeEstimateService`, `OrderDraftServiceImpl`,
`OrderServiceImpl`, `uoc-tinh-cuoc.html`, `user/order-form.html`, `OrderFeeEstimateTest`.
Hai cách tính cước chồng nhau: nhánh này tính theo **khu vực** (khoảng cách tâm khu vực + phụ phí tuyến khi liên tỉnh, `RouteQuoteService`);
`develop` có phụ phí khoảng cách **đường chim bay qua ArcGIS** (`service/geo/*`, `OrderPricingService`, cột `distance_km`/`distance_fee`
của `shipment_order`, đã có trên Aiven). Đề xuất: giữ cách tính theo khu vực làm chính (không phụ thuộc dịch vụ ngoài, ước tính và tạo
đơn dùng chung một hàm), bỏ ArcGIS khỏi luồng tạo đơn, tận dụng 2 cột `distance_km`/`distance_fee` để lưu số km và phụ phí tính theo khu vực.

### 5.5. Có thể làm thêm nếu nhóm muốn
- B9 ghi "shipper chi nhánh gửi trả hàng": hiện Manager kho gửi ghi nhận bàn giao (hoàn tất hồ sơ kèm ảnh biên nhận).
  Có thể thêm bước phân công shipper SGN đi trả hàng trước khi hoàn tất.
- Giao lại sau thất bại chỉ thao tác được khi tới giờ hẹn; kịch bản "giao thất bại lần 2" chưa được thử trên web.

## 6. Đơn kiểm thử đã tạo trên Aiven (tên người nhận "Kiểm thử nghiệm thu")

| Mã đơn | Dùng cho | Trạng thái khi bàn giao |
|---|---|---|
| UTE39091520 | Nội tỉnh SGN → SGN | Đã giao; có khiếu nại và hồ sơ bồi hoàn #39 (đã duyệt) |
| UTE28448195 | Liên tỉnh SGN → HAN | Đã giao |
| UTE24967335 | Tự mang ra chi nhánh SGN → HAN | Ở kho HAN, chưa phân công giao |
| UTE17233420 | Phạm vi HAN → DAD | Chờ lấy hàng, có khiếu nại |
| UTE42561210 | Giao lại #40 (manager1) | Giao thất bại, chờ giao lại |
| UTE25143848 | Giao lại #41 (admin) | Giao thất bại, chờ giao lại |
| UTE72432605 | Hoàn hàng #42 (admin, nội tỉnh) | Giao thất bại, hồ sơ hoàn đã duyệt |

## 7. Số liệu test khi bàn giao

`mvn -Pmomo-simulator -Dmaven.test.skip=true package` rồi `mvn test`: **1116 test, 0 failure, 2 error** (đúng 2 lỗi có sẵn ở mục 5.3).
