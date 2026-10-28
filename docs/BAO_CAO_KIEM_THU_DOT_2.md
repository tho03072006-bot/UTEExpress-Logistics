# Báo cáo kiểm thử và đối chiếu kế hoạch liên tỉnh đợt 2

Ngày kiểm tra: 23/10/2026, giờ Việt Nam. Nhánh: `feature_Tho_lien_tinh`.
Nguồn yêu cầu hiện hành: `BAN_GIAO_DOT_2.md`, `PROMPT_BAN_GIAO_PHAN_CON_LAI.md`, `PROMPT_CHATGPT_LIEN_TINH.md` và các chốt bổ sung của chủ repo.

## 1. Kết luận và giới hạn bằng chứng

Đã sửa hai test Aiven lỗi cũ, phát hiện và sửa thiếu kiểm tra kho khi lập hồ sơ giao lại/hoàn, bổ sung kiểm tra nút hoàn hàng theo giai đoạn, kiểm thử toàn bộ chiều đi/chiều hoàn và ngưỡng 15 đơn. Các thay đổi chỉ nằm trên nhánh được giao; không merge/rebase hoặc mở PR.

**Full suite cuối: 1.119 test, 0 failure, 0 error, 0 skipped, 102 suite; BUILD SUCCESS lúc 22:33:52 ngày 23/10/2026.** Hai lỗi Aiven cũ đã hết. Bảng đầy đủ từng suite ở `KET_QUA_TEST_DOT_2.md`.

Kết quả test tự động không đồng nghĩa mọi chức năng đã được thao tác trên trình duyệt thật. Nghiệm thu bước 17 trên web Aiven chưa hoàn tất: chưa có mã đơn mới để báo cáo cho đợt này. Phần 5 và 6 ghi rõ các bằng chứng còn thiếu; không dùng các mã đơn của người bàn giao như kết quả chạy mới.

## 2. Tài khoản và danh mục Aiven thực tế

Truy vấn SELECT lúc **22:19:14 ngày 23/10/2026** trong transaction chỉ đọc, không truy vấn mật khẩu, thông tin liên hệ hay in cấu hình kết nối.

| Vai trò | Tổng | Đã kích hoạt (`enabled=1`) | Bị Admin khóa |
|---|---:|---:|---:|
| USER | 8 | 7 | 0 |
| SHIPPER | 5 | 5 | 0 |
| MANAGER | 3 | 3 | 0 |
| ADMIN | 1 | 1 | 0 |
| **Tổng** | **17** | **16** | **0** |

Một USER chưa kích hoạt; không được suy ra 17 tài khoản đều đăng nhập được chỉ từ số lượng này.

| Chi nhánh | Manager | Shipper | Tổng nhân sự |
|---|---:|---:|---:|
| SGN | 1 | 2 | 3 |
| HAN | 1 | 2 | 3 |
| DAD | 1 | 1 | 2 |

Cả tám Manager/Shipper đều có chi nhánh. USER và ADMIN không tính vào bảng phân bổ nhân sự này.
Danh mục đếm trực tiếp: **3 branch, 59 province_area, 270 area_neighbor, 21 route_tariff**.

## 3. Migration và bảo toàn dữ liệu

| Công cụ đã chạy `--check` | Kết quả thực tế | Exit |
|---|---|---:|
| `UpgradeAivenBranches` | Thiếu 0/11 bảng; `max_active_orders INT NOT NULL DEFAULT 15` đã có; thiếu 0 chi nhánh/khu vực/lân cận/biểu giá | 0 |
| `UpgradeAivenDropOff` | `pickup_mode ENUM('COURIER','DROP_OFF') NOT NULL DEFAULT 'COURIER'` và `drop_off_discount DECIMAL(12,0) NOT NULL DEFAULT 3000` đã có | 0 |
| `UpgradeAivenReturnLeg` | `return_leg BIT(1) NOT NULL DEFAULT 0` đã có | 0 |
| `UpgradeAivenLegacyRoutes` | Còn thêm được 0 tuyến; **39 đơn phải xử lý tay** | 0 |

Đợt này không cần thêm migration và không chạy `--apply`. 39 đơn chuyển tiếp giữ nguyên, không tự gán tuyến hoặc sửa dữ liệu đang chạy. Các test Aiven hiện có chạy theo cơ chế rollback; các ca mới dùng H2.

Đã kiểm tra tồn tại các nhóm cấu hình riêng và chứng chỉ trước khi làm; không mở đọc nội dung, sửa hoặc commit chúng. Không sửa `Order.java`, `AppUser.java` hoặc enum `OrderStatus`. `.claude/` chỉ được bỏ qua bằng cấu hình Git cục bộ, không đưa vào commit.

## 4. Thay đổi và đối chiếu chức năng

### 4.1. Các commit code/test của đợt này

| Commit | Nội dung | Test liên quan trước khi push |
|---|---|---|
| `654acb4` | Sửa fixture thanh toán/tuyến/nhân sự trong test Aiven; hồ sơ hoàn có hiệu lực ngay khi lập | 4 / 0 fail / 0 error / 0 skipped |
| `2a7ae23` | Kho gửi không lập giao lại/hoàn thay kho nhận; nút bắt đầu/hoàn tất theo kho và giai đoạn | 58 / 0 / 0 / 0 |
| `00728df` | Luồng chiều đi và chiều hoàn qua service thực; ngưỡng nhận đơn thứ 15 và chặn đơn thứ 16 | 8 / 0 / 0 / 0 |

Test tái hiện quyền lập hồ sơ đã đỏ trước sửa: hai trường hợp RETRY/RETURN không ném `AccessDeniedException` khi Manager SGN lập cho chặng giao HAN. Sau sửa, cả mở form và gọi service trực tiếp đều bị chặn. Không bỏ/skip/@Disabled test, không đổi datasource/profile hoặc giảm điều kiện kiểm tra để làm xanh.

### 4.2. Ma trận kế hoạch B1–B11 / bước 1–17

“Test đạt” dưới đây là bằng chứng tự động trên các trường hợp đã viết, không phải tuyên bố bao phủ tuyệt đối mọi đầu vào hoặc đã kiểm tay từng trang.

| Yêu cầu | Bằng chứng chính trong suite | Trạng thái / phần còn lại |
|---|---|---|
| B1–B4; bước 1–2: 3 chi nhánh, 59 khu vực, nhân sự, bảng phụ | `BranchSeedDataTest`, `BranchSchemaSqlTest`, `UpgradeAivenBranchesTest`, `BranchAdminServiceTest`, `StaffBranchServiceTest` | Test đạt; schema/danh mục và phân bổ nhân sự đã đọc lại trên Aiven |
| B7; bước 3, 6: cước tuyến/khoảng cách dùng chung, Hỏa tốc, biểu giá | `RouteQuoteServiceTest`, `RouteTariffAdminServiceTest`, `OrderFeeEstimateTest`, `PublicValidationIntegrationTest` | Test đạt, có so sánh API ước tính với đơn liên tỉnh được tạo |
| B4; bước 4–5: tỉnh/khu vực, sổ địa chỉ, đơn nháp | `PublicValidationIntegrationTest`, `BranchSchemaSqlTest`, `UpgradeAivenBranchesTest` | Test đạt: khu vực sai tỉnh, địa chỉ cũ, quyền sở hữu, token chống tạo trùng và cascade dữ liệu phụ |
| B5; bước 7: chi nhánh, sức chứa, xếp hạng và phân công | `DispatchRulesIntegrationTest`, `AivenOperationsIntegrationTest` | Test đạt; đã bổ sung đúng ranh giới 14→15→từ chối 16 và chặn shipper sai chi nhánh ở cả chiều lấy/giao |
| B3; bước 8–9: bàn giao kho, giải phóng shipper lấy, chuyến/niêm phong/nhận kiện | `HubServiceTest`, `InterprovincialReturnIntegrationTest` | Test đạt: chiều đi SGN→HAN, quyền kho, thiếu kiện/mã niêm phong, phiên bản cũ |
| B11; bước 9b: tự mang ra chi nhánh | `DropOffIntegrationTest`, `UpgradeAivenDropOffTest` | Test đạt: giá nhất quán, không vào phân công lấy/chưa phân công, đúng kho, đủ điều kiện thanh toán, nội tỉnh→DEST_HUB/liên tỉnh→ORIGIN_HUB |
| B2, B8; bước 10–11: phạm vi chi nhánh và hồ sơ tiền/giao lại/hoàn | `OperationsControlsIntegrationTest`, `CrossRoleSynchronizationIntegrationTest`, `StaffSecurityIntegrationTest`, `InterprovincialReturnIntegrationTest` | Test đạt; đợt này sửa thêm quyền lập hồ sơ ở kho nhận và hiển thị nút theo chặng |
| B3; bước 12: hành trình, thông tin kho, trợ lý/email | `InterprovincialReturnIntegrationTest`, `AssistantIntegrationTest`, `SenderOrderMailListenerTest` | Hành trình hoàn và nội dung thông báo được test; chưa nghiệm thu email đến hộp thư thật hoặc hội thoại dịch vụ ngoài trong đợt này |
| B3; bước 13: thống kê lấy/giao và COD | `PickupStatisticsIntegrationTest`, `ShipperStatisticsServiceImplTest`, `ShipperOperationsIntegrationTest`, `CrossRoleSynchronizationIntegrationTest` | Test đạt; COD/chống thanh toán lặp và thống kê theo sự kiện có kiểm tra |
| B6; bước 14: ưu tiên và lộ trình xuất phát từ kho | `ShipperRouteOrderingTest`, `ShipperOperationsIntegrationTest` | Test đạt; dùng tâm khu vực xấp xỉ. Lưu ý điểm diễn đạt trạng thái giao thất bại ở mục 6 |
| B2; bước 15: báo cáo, hiệu suất, CSV theo chi nhánh | `ManagerReportCsvTest`, `ManagerOperationsServiceImplTest`, `LogisticsIntegrationTest` | Test đạt; CSV có nhãn chi nhánh, kiểm tra quyền và khoảng ngày |
| B10; bước 16: dữ liệu cũ và tài khoản demo | `UpgradeAivenLegacyRoutesTest`, SELECT tài khoản và `--check` thực tế | Danh mục/nhân sự đủ; **39 đơn cũ chưa được xử lý tay** |
| B9; bước 17: hoàn liên tỉnh | `InterprovincialReturnIntegrationTest`, `UpgradeAivenReturnLegTest` | Test tự động đi đủ hai chiều, chặn sai kho, có ảnh biên nhận giả lập và hành trình; **chưa hoàn tất nghiệm thu web Aiven** |

### 4.3. Hồi quy các chức năng đã được duyệt

| Nhóm | Suite đã chạy trong full test |
|---|---|
| Guest: tra cứu, QR, ước tính, trang công khai | `LogisticsIntegrationTest`, `ShipperOperationsIntegrationTest`, `FeeEstimateServiceTest`, `PublicValidationIntegrationTest` |
| User: tạo/hủy đơn, giỏ nháp, địa chỉ, thanh toán, đánh giá, khiếu nại, hồ sơ | `PublicValidationIntegrationTest`, `AccountProfileIntegrationTest`, `ServiceReviewServiceImplTest`, `LogisticsIntegrationTest`, `CrossRoleSynchronizationIntegrationTest` |
| Shipper: phân công, việc/lộ trình, trạng thái, lấy/giao thất bại, bằng chứng, COD, CSV | `DispatchRulesIntegrationTest`, `ShipperOperationsIntegrationTest`, `LogisticsIntegrationTest`, `DeliveryProofServiceImplTest`, `DeliveryProofStorageServiceImplTest`, `ShipperRouteOrderingTest` |
| Manager: tổng quan, tra soát, cảnh báo, phân công, hồ sơ, khiếu nại, cước, báo cáo | `AivenOperationsIntegrationTest`, `AivenControlsIntegrationTest`, `OperationsControlsIntegrationTest`, `StaffWorkspaceIntegrationTest`, các test `service.manager` |
| Admin: tài khoản, cấp nhân sự, chính sách, lịch cước, phiếu tiền, nhật ký/API | `AivenAccountIntegrationTest`, các test `service.admin`, `StaffSecurityIntegrationTest`, `StaffApiSecurityIntegrationTest` |
| Chung/nâng cao: OTP, đăng nhập/phiên, JWT, WebSocket, MoMo, email, media | Các test `security`, `payment`, `profile`, `NotificationWebSocketTest`, `SenderOrderMailListenerTest`, các test `service.media` |

## 5. Kết quả kiểm trên web thật

- App đã khởi động với Aiven và `ddl-auto=validate`, cổng 8080. Không có lỗi thiếu bảng của bước 1 nữa.
- Sau các sửa cuối, đã khởi động lại chính app của đợt này; Tomcat xác nhận cổng 8080 và khởi động thành công lúc **22:38:20**, tiến trình Java **41912**. Log: `target/handoff-app-final.log`. Trạng thái khởi động này không thay thế nghiệm thu tương tác trên trình duyệt.
- Đã đăng nhập thành công bằng `thotest`; mở tổng quan người dùng và tới trang tạo đơn.
- Đã đăng nhập thành công bằng `manager1`; mở màn hình kho SGN.
- Chưa xác nhận lại đăng nhập `admin`, `shipper1`, Manager/shipper HAN trên web trong đợt này. Không ghi mật khẩu vào tài liệu.
- Chưa tạo đơn Aiven mới cho bước 17, vì vậy **mã đơn nghiệm thu mới: chưa có**. Các đơn ở mục 6 của bàn giao là dữ liệu của đợt trước.

Sau đó công cụ trình duyệt báo bị tắt trong Work settings. Quyền thực hiện kiểm thử đã được chủ repo cấp, nhưng không thay thế được việc bật công cụ hoặc thông tin đăng nhập còn thiếu. Không dùng truy vấn trực tiếp để dựng trạng thái đơn giả rồi gọi là nghiệm thu web.

## 6. Việc còn lại, lý do và tiêu chí kết thúc

1. **Bước 17 trên web thật:** cần bật công cụ trình duyệt và đăng nhập được `manager_han` cùng một shipper HAN. Tạo đơn ghi “Kiểm thử”, thực hiện SGN→HAN, giao thất bại, lập/bắt đầu hoàn, chuyến HAN→SGN, nhận kho, hoàn tất kèm ảnh; ghi mã vận đơn, mã chuyến và hồ sơ vào báo cáo. Kiểm Manager SGN không bắt đầu hoàn, Manager HAN không hoàn tất; người gửi thấy đủ mốc. Test tự động đã có nhưng chưa thay thế tiêu chí này.
2. **Mục 5.5 tùy chọn:** chưa bổ sung bước phân công shipper kho gửi đi trả hàng. Luồng hiện hành theo bàn giao là Manager kho gửi xác nhận trả người gửi kèm biên nhận. Không đánh dấu đã đáp ứng trọn câu “shipper chi nhánh gửi trả hàng” của B9.
3. **39 đơn cũ:** cần Manager/nhóm quyết tuyến và xử lý nghiệp vụ riêng. Không tự UPDATE các đơn dùng chung hoặc suy đoán địa chỉ ngoài vùng.
4. **Các kiểm tra thực tế ngoài unit/integration:** giao thất bại lần hai sau giờ hẹn, email/OTP nhận thật, camera QR/ảnh/chữ ký trên thiết bị, giao diện trên thiết bị nhỏ và toàn bộ đăng nhập demo chưa được nghiệm thu lại trong đợt này. Các quy tắc server tương ứng có test tự động; không coi đó là kiểm thiết bị/dịch vụ ngoài.
5. **Sức chứa trên web:** đã kiểm tự động đúng 15/16 bằng H2, chưa tạo 16 đơn Aiven chỉ để thử tải. Không cần thêm dữ liệu dùng chung để xác minh phép đếm ở service.
6. **Điểm tài liệu cần đồng bộ:** bảng C bước 14 nói giao thất bại liên tỉnh về `DEST_HUB`; bàn giao bước 17 và code hiện hành giữ `LAST_MILE` khi vừa báo FAILED, chuyển `DEST_HUB` khi bấm “Bắt đầu hoàn”. Đợt này kiểm đúng luồng của bàn giao đợt 2, không tự đổi quy tắc giao lại/thu hồi hàng. Cần thống nhất lại câu trong kế hoạch gốc trước nghiệm thu cuối.
7. **Tích hợp develop:** đề xuất riêng ở `DE_XUAT_GOP_DEVELOP_DOT_2.md`. Develop đã chứa phần liên tỉnh đến `06ca116` và có 20 commit mới; nhánh này chưa có các sửa đó. Full test xanh của feature không chứng minh bản tích hợp xanh. Chưa merge hoặc mở PR theo đúng yêu cầu.

## 7. Lệnh và nguồn số liệu

Lệnh cuối cùng, theo yêu cầu bàn giao:

```powershell
mvn -B -Pmomo-simulator '-Dmaven.test.skip=true' package
mvn -B test
```

Lệnh đầu chỉ chuẩn bị JAR simulator; lệnh sau chạy toàn bộ test, không có tham số skip. Số liệu lấy từ dòng tổng kết Maven và đối chiếu `target/surefire-reports/TEST-*.xml`, không cộng lẫn các lần chạy riêng.

Log cục bộ (không commit): `target/handoff-final-momo-package.log`, `target/handoff-final-full-test.log`, `target/return-permissions-before.log`, `target/return-permissions-after.log`, `target/return-capacity-tests.log`, `target/account-inventory.log`, `target/UpgradeAiven*-handoff-check.log`.

Kết quả full suite cuối và bảng từng suite đã được ghi ở `KET_QUA_TEST_DOT_2.md` sau khi lệnh hoàn tất.
