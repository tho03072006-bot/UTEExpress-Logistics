# Đề xuất đưa phần hoàn thiện đợt 2 vào develop

Ngày đối chiếu: 23/10/2026. Chỉ đọc lịch sử và diff; chưa merge, rebase hoặc mở PR.

## 1. Trạng thái Git mới nhất đã kiểm tra

- Nhánh làm việc: `feature_Tho_lien_tinh`, bắt đầu đợt này tại `06ca116` sau khi fetch và pull fast-forward.
- `origin/develop`: `f2c9ca8` (Merge pull request #48).
- `git merge-base HEAD origin/develop` trả `06ca116879d3d3db6e868c5e8458006b21987fd1`.
- `develop` đã chứa toàn bộ code liên tỉnh đến `06ca116`, qua các commit tích hợp `700057b` và `0737e2c` trên nhánh của thành viên, rồi PR #48.
- Tại `00728df`, `git rev-list --left-right --count HEAD...origin/develop` trả `3 20`: ba commit mới riêng ở nhánh này, 20 commit riêng ở develop. Các commit tài liệu sau đó làm tăng vế trái.

Vì vậy, nhận định “chưa gộp” và danh sách 7 file xung đột trong bàn giao cũ không còn là kết luận cho trạng thái hiện tại. Không dùng lại danh sách đó như kết quả kiểm tra mới.

## 2. Phạm vi thay đổi đợt này

Commit `654acb4` chỉ sửa hai test, không thay đổi code vận hành:

| File | Thay đổi cần giữ |
|---|---|
| `AivenControlsIntegrationTest.java` | Hồ sơ hoàn có hiệu lực ngay: kiểm tra APPROVED, bỏ bước review; kiểm tra IN_PROGRESS sau bắt đầu hoàn. |
| `AivenOperationsIntegrationTest.java` | Tạo thanh toán COD và cấu hình tuyến/chi nhánh của shipper trong transaction tự rollback; kiểm tra shipper hai chặng, lịch sử và thông báo sau phân công. |

Commit `2a7ae23` chặn kho gửi lập hồ sơ giao lại/hoàn cho chặng giao ở kho nhận; nút bắt đầu/hoàn tất hồ sơ được tính tại service theo đúng kho, giai đoạn và trạng thái. Có kiểm tra HTTP 403 với thao tác sai kho và render Thymeleaf cho cả hai Manager/Admin ở từng chặng.

Commit `00728df` mở rộng test hoàn hàng để đi từ tạo đơn, phân công lấy, bàn giao kho, chuyến SGN → HAN, phân công giao, giao thất bại rồi mới hoàn về SGN. Bổ sung test đúng ngưỡng mặc định: nhận đơn thứ 15, từ chối đơn thứ 16. Các ca này dùng H2, không tạo đơn thật trên Aiven.

Trên develop, `AivenOperationsIntegrationTest` đã chuyển từ `OrderService.createOrder(...)` sang `OrderCreationWriter.createRouted(...)` trong test khuyến mãi. Khi tích hợp phải giữ thay đổi này, chỉ bổ sung fixture thanh toán/tuyến và các assertion mới. Không lấy nguyên file cũ đè lên file của develop.

`OperationsCaseServiceImpl` trên develop đã có `requireDeliveryHub` và các sửa khóa dòng/refresh entity. Khi tích hợp phần hoàn hàng, giữ các sửa đó, tránh thêm trùng kiểm tra kho; bổ sung `ReturnActions` cùng controller/template và test mới. Không thay toàn bộ service bằng bản của nhánh này.

Chưa thực hiện merge thử nên không khẳng định số xung đột văn bản. Dựa trên diff, hai bên sửa các đoạn phần lớn khác nhau trong test; vẫn cần kiểm tra kết quả tích hợp và chạy lại test.

## 3. Cách tính cước trên develop đã thay đổi so với bàn giao

Đã đọc `origin/develop:OrderServiceImpl`, `OrderCreationWriter` và so sánh các file liên quan:

- Luồng tạo đơn mới gọi `OrderCreationWriter.createRouted(...)`, bên trong gọi `OrderRoutingService.quote(...)` và lưu tuyến cùng transaction.
- Cách tính theo khu vực của B7 hiện đã là luồng tạo đơn mới; không cần đề xuất thay thế toàn bộ luồng bằng bản cũ của nhánh này.
- `Order.java` trên develop có các cột `distance_*` của cách tính địa chỉ/ArcGIS. Code mới của develop chủ động không ghi số liệu tuyến mới vào các cột đó để tránh hiển thị sai ý nghĩa.
- Giữ nguyên ý nghĩa dữ liệu lịch sử. Đề xuất cũ “tận dụng distance_km/distance_fee cho cước theo khu vực” cần được nhóm quyết lại trước khi làm; không tự đổi nghĩa cột và không chạy migration khoảng cách trong đợt này.

## 4. Hai phương án để nhóm lựa chọn

### A. PR nhỏ chỉ đưa phần bổ sung của nhánh này vào develop (đề xuất)

Nhóm tạo PR sau khi xác nhận nghiệm thu. Diff từ merge-base hiện gồm tám file code/test (hai test Aiven, bốn file service/controller/template, hai test phân công/hoàn hàng), cộng tài liệu đợt này; giữ toàn bộ các sửa chữa mới của develop. Chạy hai test Aiven, các test hoàn hàng/phân quyền và full suite trên kết quả tích hợp.

Lợi ích: phạm vi review nhỏ, không mang bản cũ của các service trở lại. Rủi ro: nhánh này chưa có 20 commit sửa lỗi mới của develop, nên full test xanh ở nhánh này chưa chứng minh kết quả tích hợp xanh.

### B. Nhóm chuyển chọn lọc các commit hoàn thiện sang một nhánh mới từ develop

Thực hiện trong công việc tích hợp riêng do nhóm quyết, sau đó đưa các tài liệu cần thiết. Giữ phần `OrderCreationWriter`, `requireDeliveryHub` và khóa dòng đang có trên develop; đưa phần hiển thị nút đúng kho và test bổ sung.

Lợi ích: kiểm thử ngay trên nền develop mới nhất. Rủi ro: phát sinh commit có SHA khác và cần tránh đưa trùng thay đổi nếu sau đó lại gộp toàn bộ nhánh feature. Đợt này không tạo nhánh mới hoặc tự thực hiện phương án này.

## 5. Kiểm tra cần có khi tích hợp

- Chạy `mvn -Pmomo-simulator "-Dmaven.test.skip=true" package` để tạo JAR simulator, rồi `mvn test` không skip test. Báo đủ tổng/failure/error/skipped.
- Giữ các sửa mới của develop về quyền chi nhánh, cache transaction, khóa dòng kho, kiểm tra danh sách cập nhật hàng loạt, COD và điều kiện thanh toán; không ghi đè service bằng bản feature cũ.
- Kiểm tra luồng hoàn hàng từ HAN về SGN, chặn thao tác sai kho và hành trình người gửi trên bản tích hợp.
- Đối chiếu lại `origin/develop` ngay trước khi mở PR; các SHA và nhận xét trong tài liệu này là ảnh chụp tại thời điểm kiểm tra.

Không có thao tác merge/rebase/reset/stash/force push hoặc mở PR nào được thực hiện trong đợt này.
