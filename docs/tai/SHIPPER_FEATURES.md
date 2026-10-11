# Tính năng Shipper bổ sung

Phần của Nguyễn Hữu Tài (`feature_Tai`), phù hợp luồng Spring Boot + Thymeleaf + Bootstrap + JPA + WebSocket của UTEExpress. Database đang dùng MySQL trên Aiven.

## Sáu tính năng đã thêm

| Tính năng | Cách dùng |
|---|---|
| Tìm kiếm, lọc và phân trang | Shipper → Đơn được phân công: tìm mã vận đơn, tên/số điện thoại người nhận; lọc trạng thái và ngày tạo; 10 đơn/trang. |
| Lịch sử hành trình | Chi tiết đơn → Lịch sử hành trình: thời gian, người cập nhật, trạng thái, ghi chú. Người gửi cũng xem được ở chi tiết đơn của mình. |
| Giao thất bại và giao lại | Shipper chọn lý do, ghi chú và hẹn giao lại nếu cần. Đơn thất bại có nút bắt đầu giao lại hoặc hoàn trả. |
| Thông báo nhận đơn | Manager → Phân công Shipper. Shipper nhận thông báo riêng qua WebSocket, có số chưa đọc, đánh dấu từng mục hoặc tất cả đã đọc. |
| QR vận đơn | Chi tiết đơn có QR để tải; Shipper → Quét QR chọn ảnh hoặc chụp ảnh bằng điện thoại để mở đơn được phân công. |
| Khiếu nại | Người gửi tạo hồ sơ và đính kèm ảnh tại chi tiết đơn; Shipper bổ sung thông tin; Manager → Khiếu nại để phản hồi và cập nhật trạng thái. |

## Luồng thử nhanh

1. Manager đăng nhập tại `/noi-bo/dang-nhap`, mở `/manager/phan-cong`, chọn một đơn chờ lấy hàng và Shipper đang hoạt động, điền lý do rồi phân công.
2. Shipper đăng nhập, mở `/shipper/trang-chu` hoặc chuông thông báo. Chỉ đơn được phân công mới xem và thao tác được.
3. Shipper cập nhật lấy hàng → đang giao. Nếu giao thất bại, chọn lý do và ghi chú; hẹn giao lại dùng giờ Việt Nam, trong 30 ngày tới.
4. Shipper có thể giao lại khi đơn chưa có 3 lần thất bại được ghi nhận. Mỗi lần giao lại cần ảnh và chữ ký mới trước khi xác nhận thành công; bằng chứng của lần trước không được dùng lại.
5. Người gửi mở chi tiết đơn → Khiếu nại, gửi tiêu đề, nội dung và ảnh nếu có. Manager xử lý tại `/manager/khieu-nai`; Shipper xem hồ sơ trong chi tiết đơn và bổ sung thông tin.

Mỗi vận đơn có một hồ sơ khiếu nại. Hồ sơ đã giải quyết hoặc từ chối được khóa sửa phản hồi. Ảnh JPG/PNG tối đa 5 MB, mỗi chiều tối đa 4096 px và tổng tối đa 12 triệu điểm ảnh; ảnh khiếu nại được lưu riêng trong database và chỉ người có quyền mới xem được.

Lịch sử bắt đầu ghi từ lúc dùng bản nâng cấp này; các trạng thái cũ không có dữ liệu nhật ký sẽ không được tự dựng lại. Thông báo phát sinh khi phân công qua chức năng mới. Khi WebSocket gián đoạn, số chưa đọc được kiểm tra lại mỗi 30 giây.

## Database Aiven

Ba bảng bổ sung: `order_status_history`, `shipper_notification`, `order_complaint`. Cấu trúc `shipment_order` và `app_user` không đổi. Script chỉ tạo bảng mới và không thay đổi dữ liệu cũ.

Database chung đã được nâng cấp ngày 11/10/2026. Các thành viên cùng dùng database này chỉ cần lấy code mới và khởi động lại ứng dụng; không cần chạy lại script.

Nếu dùng một database MySQL 8+ khác đã có các bảng UTEExpress, có thể kiểm tra và nâng cấp bằng công cụ dưới đây trong PowerShell tại thư mục project. Cấu hình kết nối/TLS vẫn lấy từ `config/aiven-local.properties` của máy đó.

```powershell
$env:JAVA_HOME = 'C:\Users\ADMIN\.jdks\ms-21.0.8'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -q '-DskipTests' compile dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/runtime-classpath.txt'
$shipperClasspath = 'target\classes;' + (Get-Content target/runtime-classpath.txt -Raw).Trim()
java -cp $shipperClasspath vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenShipper --check
```

Chỉ khi cần thêm ba bảng trên database khác, chạy cùng lệnh Java với `--apply` thay cho `--check`. Công cụ khóa việc nâng cấp đồng thời, kiểm tra bảng có sẵn và không tự sửa/xóa bảng sai cấu trúc. Script nằm tại `src/main/resources/db/migration/V2__shipper_features.sql`; không có Flyway tự chạy khi ứng dụng khởi động.

## Kiểm tra

Đã chạy toàn bộ **174 test bằng Java 21: 0 lỗi**. Các kiểm tra mới bao gồm truy vấn/phân trang trên H2 riêng, render Thymeleaf, CSRF và quyền truy cập, lịch sử tạo/hủy đơn, giao lại/bằng chứng mới, QR, phân công/thông báo, quyền nhận WebSocket, khiếu nại và chống ghi đè dữ liệu. JavaScript thông báo cũng đã được kiểm tra với sự kiện mới và cơ chế kiểm tra lại số chưa đọc. H2 chỉ là dependency cho test; ứng dụng vẫn dùng MySQL Aiven.

Đã thử giao diện trên H2 riêng với tài khoản và đơn mẫu: tìm kiếm, chi tiết đơn, khiếu nại, Manager phân công, Shipper nhận thông báo. Không tạo tài khoản hoặc vận đơn thử trên database chung.

Các file cấu hình database, chứng chỉ, email và JWT cục bộ tiếp tục bị ignore, không đưa vào Git.

## Bảy nâng cấp hoàn thiện khu vực Shipper

| Nâng cấp | Cách sử dụng |
|---|---|
| Giao diện điện thoại | Danh sách dạng thẻ, tab trạng thái, nút thao tác lớn; bộ lọc tự thu gọn trên điện thoại nếu chưa lọc. |
| Liên hệ và chỉ đường | Gọi người nhận, Google Maps đến điểm lấy/giao, sao chép địa chỉ tại chi tiết đơn. |
| Tiền cước chính xác | Lấy số tiền thực trả từ `order_payment`, hiển thị giảm giá; COD chưa trả mới có tiền mặt cần thu. Đã thanh toán hoặc ví điện tử không thu lại tiền mặt. Thiếu bản ghi thanh toán thì báo thiếu thông tin để hỏi Manager. |
| Công việc hôm nay | Gồm đơn đang xử lý và giao lại hẹn đến hôm nay. Ưu tiên đơn giao lại đã đến hẹn, rồi đang giao, hẹn giao lại và lấy hàng; trong mỗi nhóm ưu tiên dịch vụ hỏa tốc/nhanh. Tab đến hẹn chỉ lấy lịch hẹn của lần thất bại gần nhất và loại đơn đã đủ 3 lần thất bại. |
| Bằng chứng riêng tư | Ảnh/chữ ký mới lưu ở `uploads-private/giao-hang`; xem qua endpoint kiểm tra người gửi, Shipper được phân công hoặc Manager/Admin có tài khoản nội bộ hợp lệ. Ảnh cũ trong `uploads/giao-hang` cũng bị chặn đường dẫn công khai và chỉ đọc qua endpoint có quyền. |
| Thống kê và CSV | Chọn ngày/tuần/tháng hoặc khoảng tùy chọn tối đa 92 ngày; biểu đồ và bảng theo ngày, tải CSV UTF-8 đọc được tiếng Việt trong Excel. Nhật ký dùng thời điểm chuyển trạng thái và Shipper thực hiện; cước COD dùng `paid_at`. Đơn cũ chưa có nhật ký chuyển trạng thái dùng `updated_at` và được ghi chú rõ trên trang. |
| QR bằng camera | Nhấn Bắt đầu quét, cho phép camera và đưa QR vào khung; chỉ mã vận đơn được phân công mới mở được. Có dừng camera, tự dừng khi rời trang, báo lỗi quyền/camera và tải ảnh QR dự phòng. Không tự mở địa chỉ URL đọc từ QR. |

Các trang danh sách, chi tiết, thống kê, thông báo, QR, hành trình và khiếu nại của Shipper dùng xanh HCMUTE #0072BC, trắng và xám; hỗ trợ chế độ sáng/tối. Không cần thêm bảng/cột database cho bảy nâng cấp này.

Cước COD ở đây là **cước vận chuyển**, chưa bao gồm giá trị hàng hóa hoặc nghiệp vụ bàn giao/đối soát tiền với Manager. Bằng chứng lưu trên máy chạy server; Aiven lưu thông tin và đường dẫn. Khi chạy ở máy khác, cần sao lưu/chuyển cả thư mục ảnh cũ và `uploads-private`, hoặc dùng nơi lưu tệp chung.

Camera trực tiếp cần HTTPS hoặc localhost. Truy cập từ điện thoại qua địa chỉ IP HTTP trong mạng LAN thường không bật được camera; dùng tải ảnh QR trong trường hợp đó. Chức năng đọc camera dùng bản ZXing browser 0.1.5 lưu trong project, kèm giấy phép MIT/Apache; không tải bộ đọc từ CDN khi chạy.

## Kiểm tra bản hoàn thiện

- Java 21: **188 test, 0 lỗi**, đóng gói JAR thành công.
- Kiểm tra truy vấn mới trực tiếp trên Aiven bằng kết nối **read-only**, `ddl-auto=validate`: công việc hôm nay/đến hẹn, tiền cước, thống kê theo ngày và xuất CSV đều thành công; không tạo dữ liệu thử.
- Thử giao diện desktop 1280 px và điện thoại 390 px trên H2 riêng: không tràn ngang, sao chép địa chỉ, danh sách thẻ, thống kê theo tuần, QR và các trang chi tiết.
- Kiểm tra JavaScript camera bằng mô phỏng: yêu cầu HTTPS, lỗi quyền, chọn camera sau, bỏ QR sai, chỉ gửi một lần, dừng khi rời trang. Chưa thử quét trực tiếp bằng camera vật lý.
- Các kiểm tra tích hợp xác nhận lấy lịch hẹn mới nhất, không lẫn đơn của Shipper khác, không thu lại cước đã thanh toán, thống kê theo nhật ký thay vì ngày sửa đơn, bảo vệ CSRF, quyền xem ảnh mới/cũ và trả 404 cho tệp thiếu/đường dẫn sai.

Khởi động lại project để nạp bản mới, mở `http://localhost:8080/shipper/trang-chu`, đăng nhập bằng tài khoản Shipper hiện có. Không cần chạy lại nâng cấp database trên Aiven chung. Chưa commit/push/PR/merge.

## Sửa lỗi phiên chạy cũ và căn thanh trên

Ngày 11/10/2026 đã tái hiện lỗi 500 trên phiên STS khởi động trước khi nâng cấp. Biên dịch Java trong STS không tự thay toàn bộ controller/service đã được nạp trong JVM; giao diện mới có thể nhận dữ liệu từ phiên Java cũ. Sau khi thay Java, dừng và chạy lại ứng dụng để nạp đồng bộ bản mới.

Đã đóng gói lại và chạy bằng Java 21 trên cổng 8080, giữ kết nối Aiven và `ddl-auto=validate`. Kiểm tra trực tiếp bằng tài khoản Shipper hiện có: danh sách đơn, công việc hôm nay, đến hẹn giao lại, chi tiết đơn, lịch sử, thống kê, QR và thông báo mở thành công. Chỉ đọc dữ liệu trong các kiểm tra này.

Thanh trên và nội dung dùng chung chiều rộng tối đa 1180 px; các trang có sidebar dùng khung tổng 1430 px và cùng căn giữa. Đã kiểm tra ở màn hình 1920 px và điện thoại 390 px, không tràn ngang toàn trang.

## Đăng nhập không mở nhầm API thông báo

Các yêu cầu đếm thông báo, WebSocket, JSON và AJAX không được lưu làm trang đích sau đăng nhập. Khi hết phiên, API chạy nền trả HTTP 401; giao diện thông báo hiện lời nhắc đăng nhập lại và dừng kiểm tra định kỳ. Trang HTML được mở trước đăng nhập (ví dụ chi tiết đơn) vẫn được giữ làm trang đích; yêu cầu API cũ đã lưu trong session bị loại bỏ. Đăng nhập trực tiếp đưa Shipper về `/shipper/trang-chu`, khách hàng về `/nguoi-dung/trang-chu`.

Đã thêm sáu kiểm tra tích hợp cho chuyển hướng, giữ chi tiết đơn, loại yêu cầu API cũ, JSON/AJAX, đăng nhập khách hàng và giữ trang khách hàng. Kiểm tra JavaScript xác nhận HTTP 401 hiện lời nhắc, dừng kiểm tra thông báo và phản hồi thành công vẫn cập nhật số lượng. Khi đóng gói lại trên Windows, dừng phiên đang chạy JAR trước để tránh khóa tệp.

## Giữ menu khi chuyển trang

Với tài khoản Shipper, logo và nút Trang chủ trên thanh trên cùng dẫn về `/shipper/trang-chu`. Khi mở trang giới thiệu `/`, tra cứu hoặc tính cước, vẫn có menu đơn được phân công, thống kê, quét QR và thông báo; trên điện thoại mở bằng nút menu. Khách chưa đăng nhập dùng bố cục công khai.

Đã kiểm tra 29 test tích hợp, 0 lỗi, gồm kiểm tra menu và đường dẫn trên ba trang công khai với Shipper và khách chưa đăng nhập.

