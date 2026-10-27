# Ma trận kiểm thử lấy hàng, giao thất bại, hoàn và bồi thường

> Kết quả tiếp nối ngày 27/10/2026: xem [Báo cáo tổng thể](BAO_CAO_TONG_THE_2026_10_27.md). Full test mới đạt 1.625/1.625; app 8080 đã khởi động lại. Các giới hạn còn lại được ghi trong báo cáo mới.

Ngày thực hiện: 26/10/2026. Nhánh `feature_Tho_lien_tinh`, HEAD 46e641c cùng các thay đổi local. Chưa commit hoặc push; không gộp develop.

## 1. Kết quả cuối

| Kiểm tra | Tổng | Fail | Error | Skipped |
|---|---:|---:|---:|---:|
| Full Maven | **1.426 test / 125 suite** | **0** | **0** | **0** |
| JavaScript | **116** | **0** | — | **0** |
| Ma trận sự cố mới | **83** | **0** | **0** | **0** |
| Tên hiển thị và ảnh chi nhánh | 10, gồm 7 ca mới và 3 ca đã có | 0 | 0 | 0 |

Tăng 90 ca so với full suite 1.336 test trước đó. Full Maven kết thúc **15:58:48**, thời gian **3 phút 5 giây**. Tổng được đối chiếu từ XML của đúng lượt chạy, không cộng các kết quả cũ vào số mới.

Đã mở web để theo dõi:

- `http://localhost:8080`: app kết nối Aiven đã chạy từ lượt trước.
- `http://localhost:8085`: web nghiệm thu H2 riêng, có guard chỉ cho `jdbc:h2:mem:incident-web`, cloud giả trong bộ nhớ, tắt email cập nhật đơn. Dữ liệu mất khi dừng tiến trình.

Kết quả không đồng nghĩa mọi tình huống thực tế đều đã được nghiệm thu. Những ca còn thiếu tính năng hoặc bị chặn quyền được ghi riêng ở mục 5.

## 2. Ma trận 83 ca mới

File: `src/test/java/vn/edu/hcmute/uteexpress/tracking/ShipmentIncidentMatrixIntegrationTest.java`.

| Nhóm | Số ca | Điều kiện đã kiểm |
|---|---:|---|
| Tất cả cặp chuyển trạng thái của shipper | **49** | 7 trạng thái nguồn × 7 đích; chỉ cho đúng luồng nghiệp vụ; không sửa được trạng thái kết thúc; shipper không tự đánh dấu đã hoàn |
| Tất cả lý do giao thất bại | 6 | Ghi FAILED, không ghi đã thu COD, tăng đúng một lượt, chưa tới hẹn thì chưa giao lại |
| Tất cả lý do lấy hàng thất bại | 6 | Giữ PENDING_PICKUP, đúng nhãn dành cho người gửi, vẫn hủy được trước khi lấy |
| Báo lấy thất bại sai giai đoạn | 6 | Chặn sau khi đã lấy/đang giao/thất bại/giao xong/hủy/hoàn |
| Gửi bằng chứng ngoài giai đoạn đang giao | 6 | Không lưu bằng chứng ở trạng thái không phù hợp |
| Giả vai trò shipper | 3 | USER, MANAGER, ADMIN không được xác nhận lấy hoặc báo lấy thất bại thay shipper |
| Đổi ID để thao tác đơn người khác | 1 | Shipper khác không cập nhật hoặc ghi thất bại cho đơn không được giao |
| Thao tác đồng thời | 6 | Xem bảng bên dưới |
| **Tổng** | **83** | **Đạt tất cả** |

Sáu ca đồng thời dùng giao dịch thật, không bọc toàn bộ test trong một giao dịch chưa commit:

1. Bấm xác nhận lấy hàng hai lần: chỉ một lần thành công, không nhân đôi lịch sử lấy.
2. Bấm giao thất bại hai lần: chỉ tính một lượt, COD vẫn chưa thu.
3. Người gửi hủy đúng lúc shipper xác nhận lấy: chỉ một thao tác thành công.
4. Người gửi hủy đúng lúc kho nhận DROP_OFF tại quầy: chỉ một thao tác thành công, stage khớp trạng thái cuối.
5. Manager đồng thời lập giao lại và hoàn cho cùng đơn: chỉ một hồ sơ được tạo thành công.
6. Bấm hoàn tất bồi thường hai lần: chỉ một thao tác thành công.

Các bộ test cũ vẫn chạy đầy đủ, gồm giới hạn ba lần lấy thất bại, chặn ghi lặp trong một phút, lịch giao lại, Admin duyệt vượt giới hạn, hoàn liên tỉnh đúng hai đầu kho, bằng chứng/chữ ký, thanh toán, sức chứa và phân quyền.

## 3. Các ca đã thao tác trên web H2

| Ca | Dữ liệu kiểm thử | Kết quả quan sát |
|---|---|---|
| Khách từ chối nhận | **UTE24160799** | Shipper HAN chọn REFUSED, ghi chú; chuyển sang Giao thất bại; chưa đủ điều kiện giao lại khi chưa có hẹn |
| Hoàn liên tỉnh | UTE24160799, hồ sơ **#1** | Manager HAN lập hồ sơ, có hiệu lực ngay; bắt đầu hoàn; xếp chuyến ngược, niêm phong và xuất kho |
| Chuyến hoàn về SGN | **TC-HANSGN-261026152112ee86030f364f** | SGN thấy chuyến đang tới, kiểm niêm phong và nhận đủ kiện; xuất hiện trong mục hàng hoàn chờ trả người gửi |
| Nhận sai niêm phong không ghi chú | Cùng chuyến hoàn | Bị từ chối; chuyến vẫn ở hàng đợi nhận; không nhập kho một phần |
| Bàn giao trả người gửi | UTE24160799, hồ sơ #1 | Manager SGN điền người nhận, đính kèm ảnh chứng từ giả lập; hồ sơ **Đã hoàn tất**, có biên nhận và ảnh |
| Kiện bị ghi thiếu rồi tìm thấy | **UTE25832263** | HAN thấy trong mục thiếu kiện; nhận bổ sung có ghi chú; số kiện thiếu từ 1 về 0, chờ phân công giao tăng lên 1 |
| Khiếu nại mất hàng | **UTE26421512**, khiếu nại #1 | Manager nhận xử lý, phản hồi tra soát và lập đề xuất bồi thường 54.000 đ kèm chứng từ |
| Phân quyền bồi thường | Hồ sơ **#2** | Manager chỉ thấy Chờ duyệt; Admin khác người đề xuất duyệt được; chuyển **Đã duyệt** |
| Ghi nhận chi trả trên web | Hồ sơ #2 | **Chưa hoàn tất**: thao tác đính kèm biên nhận bị cổng quyền trình duyệt từ chối |

Các mã trên chỉ thuộc H2, không có trên Aiven. Không tạo chuyến/đơn/hồ sơ thật trong DB nhóm.

Ảnh kết quả hoàn hàng: `target/incident-return-completed.png`. Ảnh này chứng minh hồ sơ hoàn #1 đã hoàn tất, không chứng minh hồ sơ bồi thường #2 đã chi trả.

Đơn lấy hàng `UTE22592290` và DROP_OFF `UTE25695952` được chuẩn bị trên web H2; chưa thao tác tiếp trên web trong lượt này sau khi origin bị chặn. Nghiệp vụ lấy hàng/DROP_OFF vẫn được chạy bằng test tự động.

## 4. Lỗi phát hiện và đã sửa trong lượt này

### 4.1. Cache thông tin của tài khoản trước khi đổi người đăng nhập

Tên hiển thị và ảnh chi nhánh trước đây chỉ lưu theo session, chưa ràng buộc username. Nếu đổi principal trong cùng session, giao diện có thể dùng thông tin cũ.

Đã sửa:

- `DisplayNameAdvice`: chỉ dùng tên đã cache khi username trùng người đang đăng nhập; cache cũ không có chủ sở hữu được đọc lại.
- `StaffBranchPhotoAdvice`: gắn cache ảnh chi nhánh với username; chuyển SGN sang HAN trong cùng session phải tra đúng chi nhánh mới.
- Thêm 6 test `DisplayNameAdviceTest`, thêm 1 test đổi Manager trong `StaffBranchPhotoAdviceTest`; giữ đủ các test cũ.

Đây là lỗi hiển thị. Không có bằng chứng quyền thao tác server được chuyển nhầm tài khoản; các test quyền server vẫn đạt.

### 4.2. Thông báo nhận hàng hoàn hướng dẫn sai bước tiếp theo

Khi SGN nhận chuyến hàng hoàn, thông báo chung trước đây nói các đơn đã sẵn sàng phân công giao. Hàng hoàn thực tế phải trả người gửi qua hồ sơ hoàn.

Đã đổi thông báo thành **“Đã nhận đủ kiện vào kho.”** Các mục kho bên dưới thể hiện đúng bước xử lý theo loại hàng. Không đổi trạng thái/tuyến hoặc dữ liệu.

Full suite cuối đã chạy sau các sửa đổi này.

## 5. Phần chưa thể ghi nhận đạt

### 5.1. Hạn chế quyền trình duyệt

Cổng quyền trình duyệt từ chối upload biên nhận lên `localhost:8085`, rồi tiếp tục từ chối cả đọc nội dung origin. Chủ repo đã cấp quyền trong chat, nhưng lần thử tiếp theo vẫn bị cổng trình duyệt từ chối. Không dùng công cụ hoặc kênh khác để đính kèm thay.

Hồ sơ #2 được giữ ở bước Đã duyệt để người dùng thao tác thủ công. Tab được đánh dấu giữ lại cho lần tiếp tục. Test tự động hoàn tất bồi thường chạy trên database H2 riêng của test, không làm hồ sơ #2 trên web thay đổi.

### 5.2. App 8080 chưa được khởi động lại

Lệnh khởi động lại app local 8080 để nạp các sửa đổi mới vừa bị cổng duyệt trên máy từ chối; lệnh không được thực thi. Phiên web đang chạy được giữ nguyên. Các sửa đổi cache và câu thông báo đã được kiểm bằng full test nhưng cần khởi động lại app để nạp vào JVM đang chạy.

### 5.3. Luồng xác nhận mất hàng chưa có tính năng riêng

Hiện hệ thống có ghi thiếu kiện, nhận bổ sung, khiếu nại và bồi thường. Chưa có dữ liệu nghiệp vụ riêng phân biệt “đã xác nhận mất hàng” với hư hỏng/bồi hoàn cước. Hoàn tất hồ sơ bồi thường hiện không tự đóng vận đơn hoặc giải phóng tải shipper.

Vì vậy **không ghi đạt** cho một luồng riêng “xác nhận mất → khóa giao/giao lại/COD → kết thúc vận đơn → giải phóng tải”. Bộ 83 ca mới kiểm quy tắc hiện có; không thay thế tính năng chưa được triển khai. Chưa thêm bảng/migration hoặc sửa OrderStatus để mô phỏng tính năng này.

Tương tự, xác nhận lấy hàng hiện chưa bắt buộc ảnh/chữ ký bàn giao ban đầu; test kiểm đúng quy tắc đang có, không chứng minh hàng vật lý đã được bàn giao.

### 5.4. Giới hạn phạm vi

Không thể suy ra không còn lỗi từ một bộ test hữu hạn. Chưa thử camera vật lý, nhận email thật, thanh toán tiền thật, Groq thực tế, tải lớn hoặc mọi trình tự thao tác đồng thời. 39 đơn Aiven cũ cần xử lý tuyến thủ công vẫn giữ nguyên. Không skip hay vô hiệu hóa test nào để có kết quả xanh.

## 6. Lệnh và bằng chứng

```powershell
mvn -B -Dtest=ShipmentIncidentMatrixIntegrationTest test
mvn -B '-Dtest=DisplayNameAdviceTest,StaffBranchPhotoAdviceTest' test
mvn -B -Pmomo-simulator '-Dmaven.test.skip=true' package
mvn -B test
node --test src/test/js/*.test.cjs
```

Lệnh package là bước chuẩn bị simulator; full `mvn test` sau đó chạy tất cả test, không loại test Aiven. Các test Aiven rollback đã được cho phép vẫn đạt.

Log trong `D:\WEB\Project_CK\target\`:

- `incident-final-package.log`, `incident-final-full-test.log`, `incident-final-test-summary.csv`.
- `incident-final-js-test.log`, `incident-matrix-test.log`, `incident-account-cache-test.log`.
- `incident-web.log`: khởi tạo web H2 và các mã kiểm thử.
- `incident-return-completed.png`: ảnh hồ sơ hoàn đã hoàn tất.
- `IncidentWebApplication.java`: công cụ nghiệm thu tạm, có guard H2; mật khẩu nhận qua biến môi trường, không ghi mật khẩu vào repo.

Không chạy `--apply`, không commit/push, không chỉnh file bí mật, không sửa `Order.java`, `AppUser.java` hoặc enum OrderStatus. Không dùng stash/reset/checkout--/clean/rebase. Các thay đổi có từ trước được giữ nguyên.
