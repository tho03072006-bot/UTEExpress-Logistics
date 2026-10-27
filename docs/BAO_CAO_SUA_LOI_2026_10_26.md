# Kết quả cập nhật Git và sửa logic điều phối — 26/10/2026

> Lượt kiểm thử sự cố tiếp theo đã mở rộng lên 1.426 test Java và 116 test JavaScript. Xem [ma trận kiểm thử sự cố](MA_TRAN_KIEM_THU_SU_CO_2026_10_26.md) để biết kết quả web và các phần còn bị chặn. Các số liệu bên dưới thuộc lượt sửa điều phối trước đó.

## Kết quả cuối

Đã sửa hai lỗi đã xác nhận, đồng thời xử lý các điểm liên quan ở bộ lọc cảnh báo và danh sách nhận đơn thu hồi. **Full Maven đạt 1.336 test / 123 suite, 0 failure, 0 error, 0 skipped**; **JavaScript đạt 116/116**, không skipped.

App bản đã sửa đang chạy tại **http://localhost:8080**, PID 12784 khi khởi động lúc 14:24. Đã đăng nhập Manager SGN và kiểm tra trang cảnh báo trên trình duyệt. Không có lỗi máy chủ/template trong lượt kiểm app này.

Chưa commit hoặc push; giữ nguyên nhánh `feature_Tho_lien_tinh` theo lựa chọn của chủ repo. Không gộp develop.

## 1. Git và bảo toàn thay đổi

Đã chạy:

```powershell
git fetch origin
git pull --ff-only
```

Pull nhánh hiện tại trả **Already up to date**. Các nhánh remote đã fetch:

| Nhánh | Commit |
|---|---|
| feature_Tho_lien_tinh | 46e641c |
| develop | 07a9337 |
| feature_Thang | 66d524e |
| feature_Tai | 62056d1 |
| feature_Tho | 9746ad7 |
| main | beaadae |

`feature_Tho_lien_tinh` so với upstream là 0 ahead / 0 behind. develop có thêm 8 commit so với HEAD; chủ repo đã chọn **sửa trên nhánh hiện tại, không gộp develop**. Kết quả test thuộc HEAD 46e641c cùng toàn bộ thay đổi local, chưa bao gồm 8 commit này.

Trước khi sửa, đã lưu bản sao và SHA-256 của **32 file đang sửa/chưa được theo dõi** vào thư mục Git bỏ qua:

```text
target/fix-baseline-20261026-140934/
```

Không có file nào trong bản lưu bị mất. Chỉ sửa bổ sung ở các file cần cho nghiệp vụ dưới đây; các thay đổi ban đầu còn nguyên. Không dùng stash/reset/checkout--/clean/rebase, không tạo merge commit, không stage file.

## 2. Những lỗi đã sửa

### 2.1. Giao lại vượt sức chứa shipper

Trước sửa: một đơn FAILED không nằm trong tải active; sau khi shipper đã được gán đủ 15 đơn khác, giao lại đơn này vẫn thành công, làm tải lên 16/15.

Sau sửa:

- Kiểm sức chứa theo chính sách hiện hành trước khi đổi FAILED → IN_TRANSIT, dùng chung `DispatchRoutingPolicy.requireCapacity()` với phân công.
- Khóa dòng shipper trước khi khóa đơn, cùng thứ tự với phân công. Hai đường vào `updateStatus` đều dùng READ_COMMITTED để đọc tải đã commit mới nhất sau khi chờ khóa.
- Chặn trước khi ghi trạng thái, lịch sử hoặc hoàn tất hồ sơ giao lại.
- Đơn PICKED_UP vốn đã nằm trong tải active vẫn bắt đầu giao được khi tải bằng giới hạn, vì thao tác này không cần thêm chỗ trống.
- Áp dụng cả cập nhật một đơn và cập nhật hàng loạt qua `ShipperBulkService`.

File chính: `TrackingServiceImpl`, `OperationsCaseServiceImpl`, `DispatchRoutingPolicy`.

### 2.2. Không đổi được shipper đã phân công giao từ kho

Trước sửa: lần phân công đầu chuyển tuyến DEST_HUB → LAST_MILE, đơn còn PICKED_UP. Cả phân công lại và thu hồi đều từ chối trạng thái này.

Sau sửa:

- Cho thu hồi PICKED_UP + LAST_MILE khi chưa bắt đầu giao, áp dụng DROP_OFF nội tỉnh và đơn liên tỉnh đã tới kho nhận.
- Giữ các kiểm tra chi nhánh, sức chứa, shipper đang hoạt động, dữ liệu trang cũ và người đang giữ đơn.
- Giữ `pickupShipper` lịch sử; chỉ đổi `deliveryShipper` và người hiện đang giữ đơn.
- Vẫn chặn đơn IN_TRANSIT và tuyến đang hoàn.
- **Đồng bộ truy vấn HELD** để danh sách và số đếm cảnh báo nhận đúng các đơn mới được phép đổi ca. Nếu chỉ sửa service, Manager sẽ chưa nhìn thấy đơn trên giao diện.

File chính: `DispatchRoutingPolicy`, `OperationsOverviewRepository`.

### 2.3. Gợi ý nhận đơn thu hồi hiển thị cả shipper đã đủ tải

Trước sửa: dropdown vẫn cho chọn người giữ 15/15; khi gửi yêu cầu mới bị máy chủ từ chối.

Sau sửa: chỉ đưa người còn sức chứa vào danh sách. Giữ kiểm tra lại ở server để xử lý thay đổi tải sau khi mở trang. Thông báo khi danh sách trống đã giải thích cả điều kiện đang hoạt động và còn sức chứa.

File chính: `DispatchService`, `templates/manager/operations-alerts.html`.

## 3. Test hồi quy

Thêm `src/test/java/vn/edu/hcmute/uteexpress/tracking/DispatchRecoveryIntegrationTest.java`, gồm 12 ca trên H2 in-memory được chặn bằng fixture kiểm URL:

1. Đủ 15 active thì từ chối giao lại, không đổi trạng thái/lịch sử.
2. Có 14 active thì giao lại dùng đúng chỗ thứ 15.
3. Dùng giới hạn chính sách hiện hành, không cố định số 15 trong code.
4. Đơn vốn active vẫn bắt đầu giao được khi đủ tải.
5. Giao lại hàng loạt không dùng chỗ trống cuối hai lần.
6. Phân công và giao lại đồng thời chỉ một thao tác nhận chỗ cuối.
7. Hai lượt giao lại đồng thời chỉ một thao tác nhận chỗ cuối.
8. DROP_OFF nội tỉnh đổi được người giao trước khi xuất phát.
9. Liên tỉnh đổi người giao tại kho nhận và giữ đúng shipper lấy hàng; số đếm HELD tăng đúng.
10. Đổi ca vẫn chặn sai chi nhánh, trang cũ, đơn đang giao; Admin tiếp quản được.
11. Shipper đủ tải không có trong gợi ý và bị từ chối khi giả gửi yêu cầu.
12. Luồng HTTP Manager: GET cảnh báo hiển thị đơn, POST có CSRF đổi shipper thành công, shipper cũ bị chặn cập nhật đơn sau bàn giao; số đếm cảnh báo khớp.

Các race test dùng giao dịch thật trên H2, không bọc cả test trong một giao dịch rollback nên hai luồng thực sự thấy dữ liệu đã commit. Không tạo đơn test lâu dài trên Aiven. Test Mockito hiện có được cập nhật đúng phương thức khóa và kiểm thứ tự khóa shipper trước đơn.

Lệnh cuối đã chạy:

```powershell
mvn -B -Pmomo-simulator '-Dmaven.test.skip=true' package
mvn -B test
node --test src/test/js/*.test.cjs
```

Lệnh package là bước chuẩn bị simulator theo hướng dẫn dự án; **full `mvn test` chạy tất cả, không loại/skip test hoặc thêm `@Disabled`**. Không sửa cấu hình test sẵn có để làm xanh.

| Kiểm tra cuối | Kết quả |
|---|---|
| Package simulator | BUILD SUCCESS |
| Full Maven | **1.336 test, 0 failure, 0 error, 0 skipped, 123 suite** |
| Thời điểm full Maven kết thúc | 14:25:51 ngày 26/10/2026; thời gian 3 phút 7 giây |
| DispatchRecoveryIntegrationTest | 12/12 |
| TrackingServiceImplTest | 18/18 |
| AivenAccountIntegrationTest | 6/6 |
| AivenOperationsIntegrationTest | 2/2 |
| AivenControlsIntegrationTest | 2/2 |
| JavaScript | 116 pass, 0 fail, 0 cancelled, 0 skipped |
| git diff --check | Không có lỗi khoảng trắng; cảnh báo LF/CRLF của README có sẵn |

## 4. Kiểm tra app thực tế

- Dừng đúng app kiểm thử cũ PID 32752 sau khi đối chiếu log/thời điểm khởi động, chạy bản mới ở cổng 8080.
- Khởi động thành công với Aiven và `ddl-auto=validate`; không cần migration mới.
- Đăng nhập Manager SGN; mở `/manager/canh-bao?alert=HELD`, hiển thị 3 đơn đã có và form chuyển shipper.
- Dropdown đã mở hiện người nhận đang giữ 7/15, cùng ô lý do và nút thu hồi. Không submit đổi shipper trên Aiven.
- Không có ERROR/template exception trong log app của lượt kiểm này.
- Tương tác ghi đầy đủ qua HTTP được kiểm trên H2 ở ca số 12; dữ liệu Aiven dùng chung chỉ được đọc trên các trang này ngoài đăng nhập/session.

Ảnh bằng chứng local: `target/fix-held-alerts.png`. Ảnh là trạng thái form đã mở, không phải bằng chứng một thao tác thu hồi trên Aiven đã được gửi.

## 5. Phạm vi, dữ liệu và việc còn lại

- Đã sửa toàn bộ lỗi đã xác nhận trong lượt rà soát này. Chưa có lỗi mới đã xác nhận sau khi chạy bộ test cuối; điều đó không chứng minh hệ thống không còn lỗi ở mọi tổ hợp dữ liệu/thao tác.
- Không sửa `Order.java`, `AppUser.java` hay enum OrderStatus. Không đọc/sửa file bí mật, không thêm migration hoặc chạy `--apply`.
- Không tạo/xóa tài khoản hay sửa dữ liệu nghiệp vụ lâu dài trên Aiven. Các test Aiven rollback đã được chủ repo cho phép vẫn chạy đầy đủ.
- **39 đơn cũ cần xử lý tuyến thủ công** vẫn là việc dữ liệu đã biết; không tự chọn tuyến cho dữ liệu nhóm hoặc đơn ngoài vùng phục vụ.
- Chưa chạy lại trọn hành trình hoàn liên tỉnh bằng thao tác ghi trên web Aiven; test tích hợp luồng hoàn vẫn đạt. Race test mới chạy trên H2, chưa thử tranh chấp bằng dữ liệu lâu dài trên Aiven.
- Chưa nghiệm thu lại camera vật lý, email thật, Groq thực tế hoặc thanh toán ví thật.
- 8 commit develop chưa được gộp theo lựa chọn của chủ repo. Khi nhóm đưa chúng vào nhánh này, cần chạy test lại trên bản đã kết hợp.
- Chưa commit/push. Các bản sao, log và ảnh trong `target/` được Git bỏ qua.

## 6. Bằng chứng

Trong `D:\WEB\Project_CK\target\`:

- `fix-final-package.log`, `fix-final-full-test.log`, `fix-final-test-summary.csv`: kết quả cuối; số liệu CSV đối chiếu 123 XML của đúng lượt chạy.
- `fix-final-js-test.log`: 116 test JavaScript.
- `fix-dispatch-related.log`: lượt test liên quan ban đầu, 43/43.
- `fix-dispatch-web.log`: 12 ca hồi quy sau khi đồng bộ cảnh báo/HTTP.
- `fix-local-app.log`, `fix-held-alerts.png`: app 8080 và giao diện.
- `fix-baseline-path.txt`, thư mục baseline cùng `manifest.json`: bản lưu 32 file ban đầu và checksum.

Các log giai đoạn trước như `fix-full-test.log` (1.335 test) được giữ để tra cứu; **số liệu cuối là `fix-final-full-test.log` (1.336 test)** sau khi thêm ca HTTP và sửa bộ lọc cảnh báo.
