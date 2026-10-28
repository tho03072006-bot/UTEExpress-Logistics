# Manager/Admin — kiểm soát vận hành 16/10/2026

Tài liệu này cập nhật và thay thế các mô tả đã cũ trong OPERATIONS_UPGRADE.md về phân công, khiếu nại, số bảng và giới hạn giao lại. Thắng phụ trách Manager/Admin; shared code của User/Shipper được chỉnh ở các điểm cần dùng chung phân quyền, cước, lịch giao lại và đối soát. Không thay thế toàn bộ phần của thành viên khác.

## Phân quyền

| Chức năng | Manager | Admin |
| --- | --- | --- |
| CRUD tài khoản Admin/Manager/User/Shipper | Không | Có; bảo vệ bản thân, Admin cuối cùng và dữ liệu nghiệp vụ |
| Cấp Admin/Manager | Không | Cấp nội bộ, không đăng ký/khôi phục bằng OTP công khai |
| Phân công đơn chờ lấy, khuyến mãi, báo cáo | Có | Có |
| Xem cảnh báo, hiệu suất, bảng cước và lịch cước | Có | Có |
| Sửa chính sách, giá gốc, lập/hủy lịch cước | Không | Có |
| Đề nghị giao lại, duyệt trong giới hạn, xử lý hoàn hàng | Có | Có |
| Duyệt thêm lượt giao khi đã hết giới hạn | Không | Có |
| Lập đề nghị nộp thiếu/hoàn tiền thừa/bồi hoàn cước | Có | Có |
| Duyệt/từ chối hồ sơ tiền; ghi nhận chứng từ chi tiền | Không | Có, khác người lập |
| Nhận và phản hồi khiếu nại | Hồ sơ chưa có người phụ trách hoặc do mình phụ trách | Có thể tiếp quản |
| Nhật ký quản trị | Không | Chỉ đọc |

Ẩn nút không phải cơ chế bảo vệ duy nhất: service kiểm tra lại tài khoản/quyền từ database. POST khu nội bộ có CSRF. Tài khoản khóa, đổi quyền hoặc đặt lại mật khẩu không được dùng lại phiên cũ để giữ quyền. Không đưa actor/role do form gửi vào làm căn cứ cấp quyền.

## Các chức năng cập nhật

### 1. Chính sách và hàng đợi cảnh báo

- `/admin/chinh-sach-van-hanh`: Admin cấu hình hạn lấy hàng, hạn giao theo ba dịch vụ, số lần giao thất bại tối đa, hạn phản hồi/giải quyết khiếu nại. Có lý do, revision và nhật ký.
- Kiểm tra super-express <= express <= standard; phản hồi <= giải quyết; các giới hạn số phải nằm trong khoảng trên form và tại server.
- `/manager/canh-bao`: chưa phân công, quá hạn lấy/giao, đến hẹn giao lại, nhiều lần thất bại; kèm khiếu nại quá hạn và chênh lệch tiền quá 7 ngày từ lúc xác nhận phiếu.
- Mốc quá hạn đơn tính từ ngày tạo, không bị xóa cảnh báo khi chỉ sửa ghi chú. Đơn kết thúc không nằm trong hàng đợi giao trễ.
- Đây là ngưỡng demo theo giờ liên tục, giờ Việt Nam; chưa có lịch ngày lễ, ca làm, tuyến hay SLA pháp lý theo hợp đồng.

### 2. Giao lại và hoàn hàng

- `/manager/ho-so-van-hanh`: lập từ liên kết vận đơn trên cảnh báo. Kiểm tra đơn FAILED, Shipper hoạt động, revision còn mới và không có hồ sơ đơn đang xử lý.
- Lịch giao lại phải sau hiện tại và trong 30 ngày. Quá số lần thất bại theo chính sách cần Admin duyệt; mỗi hồ sơ chỉ cho thêm một lượt. Shipper bắt đầu lượt giao thì hồ sơ được tiêu thụ/hoàn tất, không thể tái sử dụng.
- Đơn đang chờ hoàn không được giao lại. Đơn FAILED thiếu lịch sử/lịch hẹn phải có hồ sơ Manager; không tự đưa vào danh sách đến hẹn.
- Điều kiện đến hẹn dùng chung giữa cảnh báo Manager, danh sách công việc, bộ đếm và cập nhật Shipper. Danh sách hôm nay vẫn hiển thị lịch hẹn trong hôm nay nhưng ưu tiên lịch đã đến giờ trước.
- Hoàn hàng: đề nghị -> duyệt -> đang hoàn -> ảnh biên nhận + tên người gửi nhận lại -> RETURNED + lịch sử vận đơn. Shipper không được POST trực tiếp để tự đánh dấu RETURNED.

### 3. Tiền cước nộp thiếu/thừa

- Phiếu gốc đã xác nhận được giữ nguyên số tiền. Không sửa phiếu cũ để làm mất chênh lệch.
- Manager có thể xác nhận số thực nhận thừa nếu ghi rõ lý do; khoản thiếu/thừa được lập hồ sơ riêng, VND nguyên dương tối đa 12 chữ số.
- Khi tạo hồ sơ kiểm tra cả tiền đã duyệt lẫn tiền đang được giữ bởi đề nghị chờ duyệt; không lập vượt số còn thiếu/thừa.
- Hồ sơ cần ảnh chứng từ và mã biên nhận. Admin khác người lập duyệt; nộp bổ sung được trừ thiếu sau khi duyệt ảnh biên nhận. Hoàn tiền thừa chỉ ghi nhận chi xong sau bước hoàn tất kèm ảnh/mã chứng từ ngoài hệ thống.
- UUID duy nhất tại database chống gửi lại cùng một form; khóa dòng/revision bảo vệ duyệt và hoàn tất lặp. Hai form mới có hai UUID không phải cùng yêu cầu; tổng tiền vẫn bị giới hạn theo số còn lại.
- Bộ đọc tiền chấp nhận số nguyên hoặc nhóm nghìn thống nhất, không hiểu `1.5` thành 15 VND, không nhận NaN, số khoa học, số âm, số lẻ hoặc nhóm phân tách lẫn lộn.

### 4. Khiếu nại và bồi hoàn cước

- Nhận phụ trách; Manager không ghi đè hồ sơ do Manager khác phụ trách, Admin có thể tiếp quản. Phản hồi/bổ sung được ghi thêm vào `complaint_activity`, không ghi đè lịch sử đối thoại cũ.
- Có cảnh báo phản hồi lần đầu/giải quyết quá hạn. Nhận phụ trách nhưng chưa phản hồi không được tính là đã trả lời khách.
- Từ chi tiết khiếu nại có thể đề xuất bồi hoàn **cước đã thanh toán**. Không vượt cước đã trả, không áp dụng khiếu nại đã từ chối; cần ảnh của khiếu nại hoặc chứng từ đề xuất.
- Admin độc lập duyệt, sau đó ghi chứng từ chi tiền thủ công. APPROVED không có nghĩa là đã trả tiền. Khiếu nại đã có bồi hoàn được duyệt/chi không được chuyển sang REJECTED mâu thuẫn với hồ sơ tiền.
- Quy trình này không hoàn tiền tự động, không xác minh ảnh biên nhận với ngân hàng và không sửa payment cũ thành giao dịch chi tiền.

### 5. Phiên bản cước theo thời điểm

- `/admin/lich-cuoc`: Admin lập khoảng hiệu lực `[bắt đầu, kết thúc)`, bắt đầu sau ít nhất 1 phút, kết thúc trong 2 năm. Số tiền nguyên không âm, tối đa 9 chữ số; hai mức giá không cùng bằng 0.
- Cùng dịch vụ không được trùng lịch chưa hủy; hai khoảng chạm đầu/cuối được phép. Khóa bảng giá gốc của dịch vụ để tuần tự hóa việc lập/hủy lịch.
- Chỉ hủy trước khi bắt đầu; không sửa/xóa lịch sử giá. Giao diện phân biệt sắp áp dụng/đang áp dụng/đã kết thúc/đã hủy.
- Ngoài khoảng có phiên bản, dùng giá gốc. Không sửa giá gốc khi phiên bản đang hiệu lực. Báo giá/tạo đơn dùng cùng service; đơn đã tạo giữ giá cước/payment tại lúc tạo.
- Manager chỉ xem; gửi POST vào URL Admin vẫn bị từ chối.

### 6. Hiệu suất và điều phối

- `/manager/hieu-suat-shipper`: tải đang chờ lấy/đang giao, giao thành công, FAILED đang cần xử lý và hoàn hàng, phân trang 20 Shipper.
- Tỷ lệ trên trang này và báo cáo tổng hợp = DELIVERED / (DELIVERED + RETURNED), 0 khi chưa có đơn kết thúc. FAILED còn có thể giao lại, không coi là kết thúc. Hiệu suất Shipper là số tích lũy theo Shipper hiện đang được gán, không phải báo cáo ca làm hoặc lịch sử chuyển tuyến; báo cáo tổng hợp vẫn lọc theo ngày tạo đơn.
- Giữ controller phân công chung từ develop để không có hai route `/manager/phan-cong`; bổ sung kiểm tra Shipper bị Admin khóa và nhật ký, giữ lịch sử/thông báo phân công hiện có.

## Database và bảo mật

- Runtime chỉ Aiven MySQL, TLS `VERIFY_IDENTITY`, `ddl-auto=validate`. Sai hoặc thiếu cấu hình thì dừng, không tự rơi về localhost/H2. H2 chỉ tồn tại trong test cô lập, không đóng gói production.
- Schema Aiven hiện 21 bảng. Migration bổ sung `operations_policy`, `operations_case`, `complaint_activity`, `shipping_tariff_version`; thêm `request_token` nullable và unique cho hồ sơ cũ tương thích. Không DROP/TRUNCATE hoặc sao chép đè dữ liệu nhóm.
- `UpgradeAivenControls --apply` đã chạy trên dịch vụ đang cấu hình ở máy này. Máy khác dùng chung dịch vụ không cần khởi tạo database mới. Nếu nâng cấp dịch vụ khác, dừng app của nhóm và kiểm tra schema trước; MySQL DDL tự commit.
- `InitializeAivenDatabase` chỉ dành cho database trống, không dùng để nâng cấp database đang có dữ liệu.
- Không xóa database/bảng MySQL vật lý trên máy; chỉ loại bỏ kết nối local, theo yêu cầu đã điều chỉnh của bạn.
- `config/aiven-local.properties` và cấu hình SMTP/JWT private tiếp tục được giữ riêng, không thay giá trị. `.example` chỉ chứa mẫu. Maven loại cả bản sao cấu hình local/cloud, `.env` và khóa/chứng chỉ khỏi JAR; `.gitignore` không xóa bí mật đã từng commit trong lịch sử.
- Ảnh: kiểm tra nội dung JPG/PNG thật, tối đa 5 MB, giới hạn kích thước và số điểm ảnh; endpoint nội bộ kiểm tra quyền, không cache. Danh sách hồ sơ dùng DTO không tải BLOB chứng từ hàng loạt.

## Giới hạn cần demo đúng

“COD” trong mô hình đang có là **tiền cước vận chuyển Shipper thu**, không phải tiền hàng shop nhờ thu hộ. Chưa có giá trị hàng, bảo hiểm, hạn mức bồi thường hàng, chuyển khoản ngân hàng, API hãng vận chuyển, nhiều chi nhánh/kho hoặc phân vùng Manager. Không mô tả những phần này là đã triển khai.

Tham khảo luồng công khai, không sao chép chính sách/giá của hãng vào hệ thống:

- [GHN — thông tin dịch vụ và bảng giá](https://ghn.vn/pages/bang-gia-moi-sieu-tiet-kiem).
- [J&T — chính sách](https://jtexpress.vn/vi/chinh-sach).
- [GHTK — trạng thái webhook đơn hàng](https://api.ghtk.vn/docs/submit-order/webhook/).

## Kiểm tra và demo

1. Đăng nhập nội bộ với Admin và Manager được cấp; lần đầu phải đổi mật khẩu. Không chia sẻ mật khẩu qua Git/chat.
2. Manager thử mở URL Admin quản lý tài khoản/chính sách/giá: bị chặn. User/Shipper cũng không vào được hồ sơ nội bộ.
3. Thử dữ liệu trống, chỉ khoảng trắng, số âm/lẻ/vượt trần, ngày ngược/quá xa, ảnh giả/ảnh quá lớn, ID không tồn tại, form cũ và POST thiếu CSRF.
4. Đơn FAILED: đề nghị giao lại, chưa đến giờ không chạy được; khi hết giới hạn Manager không duyệt được nhưng Admin duyệt được. Hồ sơ đã tiêu thụ không dùng lần hai.
5. Đề nghị hoàn: không thể hoàn tất trước khi duyệt/bắt đầu/đính kèm ảnh. Sau hoàn tất có lịch sử RETURNED và không giao lại.
6. Phiếu nộp thiếu/thừa: thử vượt tiền còn lại, gửi lặp; Manager không duyệt tiền; Admin không tự duyệt phiếu mình lập; kiểm tra tiền gốc và ảnh còn nguyên.
7. Khiếu nại: nhận phụ trách, thêm hai phản hồi và kiểm tra lịch sử; đề nghị bồi hoàn không vượt cước; duyệt chưa đồng nghĩa đã chi.
8. Lập hai lịch cước trùng phải bị chặn; chạm biên được phép. Kiểm tra giá trước/trong/sau thời hạn và đơn cũ không đổi tiền.

Kết quả xác nhận ngày 16/10/2026, Java 17:

- `mvn -q clean verify` thành công; sau điều chỉnh thống nhất tỷ lệ báo cáo, chạy lại `mvn -q verify`: **419 test, 0 failure, 0 error, 0 skipped**.
- Bao gồm validation, phân quyền, CSRF, binding form, render Thymeleaf, luồng nghiệp vụ H2 cô lập và kiểm tra Aiven thật bằng giao dịch rollback; có hai test mới riêng cho khóa/quy trình hoàn hàng và xử lý khoản thiếu trên MySQL thật.
- Kiểm tra kết nối cuối: **Aiven MySQL 8.4, TLS xác minh danh tính, đủ 21 bảng và 3 cột bảo vệ tài khoản**.
- Đóng gói JAR thành công; kiểm tra danh sách bên trong: **0 cấu hình private/chứng chỉ/khóa**, **0 thư viện H2**.
- `git diff --check` không có lỗi; không còn unmerged entry. Hướng dẫn 12 nhóm commit đã đối chiếu đủ các file thay đổi thuộc phạm vi, không kèm README/backup/.project/tệp gõ nhầm. Chưa commit/push hoặc merge develop.

Test không chứng minh không còn mọi lỗi có thể xảy ra; chưa có kiểm thử tải, mạng chập chờn thực địa, thao tác trực tiếp trên trình duyệt với từng tài khoản hoặc xác minh chứng từ bên ngoài. Checklist demo ở trên là bước tiếp theo trước khi nhóm chốt phát hành.

Lệnh và nhóm commit: xem COMMIT_PLAN_2026_10_16.md. Không commit README.md, backup, `.project`, cấu hình private hoặc tệp lệnh gõ nhầm.
