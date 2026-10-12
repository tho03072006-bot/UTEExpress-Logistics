# Thanh toán cước qua MoMo TEST

UTEExpress tham khảo cấu trúc `MomoProperties`, `MomoApiClient`, `MomoPaymentService` và luồng QR/thẻ trong project rạp `D:/CNPM_NHOM8`. Project rạp được đọc để tham khảo, không chỉnh sửa. Thanh toán cước UTEExpress gọi API thử nghiệm MoMo, thay màn hình giả lập tự ghi “đã thanh toán”. VNPay chưa tích hợp nên không cung cấp như một lựa chọn hoạt động.

## Cấu hình

Trong file **được Git bỏ qua** `src/main/resources/application-local.properties`, đặt `momo.enabled=true`, `momo.partner-code`, `momo.access-key`, `momo.secret-key` bằng bộ thông tin merchant **TEST**. Không đưa khóa vào source, ảnh demo, báo cáo, log hay commit. File `.example` chỉ chứa placeholder. Có thể dùng biến môi trường `MOMO_ENABLED`, `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` và `APP_BASE_URL` thay cho file local.

Endpoint được giới hạn ở `https://test-payment.momo.vn`. `app.base-url=http://localhost:8080` cho demo local; khi có máy chủ công khai dùng URL HTTPS của ứng dụng. Thiếu thông tin hoặc cấu hình endpoint production làm chức năng chưa sẵn sàng, không xác nhận thanh toán thay thế.

## Luồng demo

1. Đăng nhập User, tạo đơn và chọn MoMo TEST; số tiền là cước sau mã giảm giá, tính trên máy chủ, đơn vị VND.
2. Chọn **Tạo QR MoMo** (`captureWallet`) hoặc **Ví / thẻ qua MoMo** (`payWithMethod`). Dùng app/tài khoản hoặc thẻ thử nghiệm được MoMo hướng dẫn. QR được tạo ngay trên máy chủ UTEExpress, không gửi mã giao dịch sang dịch vụ ảnh bên thứ ba.
3. Hoàn tất ở MoMo TEST. Trang QR kiểm tra trạng thái tự động; nút **Kiểm tra kết quả thanh toán** cũng gọi API query từ máy chủ.
4. Chỉ phản hồi query từ HTTPS MoMo đã khớp đối tác, requestId, orderId, số tiền, hoặc IPN đã kiểm HMAC-SHA256 mới chuyển cước dương sang PAID. `resultCode=0` trên URL quay về không thể tự xác nhận.

API MoMo dùng số nguyên VND từ 1.000 đến 50.000.000 đồng. Phương thức thẻ trên trang MoMo cần ít nhất 10.000 đồng theo tài liệu; số tiền thấp hơn nên dùng ví/QR. Cước 0 đồng do miễn toàn bộ được ghi nhận bằng `WAIVED-<orderId>` từ tính toán máy chủ và không gọi MoMo.

## Localhost, lỗi và giao dịch lặp

MoMo không gọi IPN vào localhost; query xác minh trên trang QR hoặc sau khi quay về vẫn hoạt động. IPN công khai chỉ có tại `POST /thanh-toan/momo/ipn`, được miễn CSRF riêng tại endpoint này và yêu cầu chữ ký hợp lệ. Trang quay về `GET /thanh-toan/momo/ket-qua` chỉ điều hướng đến trang thanh toán có kiểm quyền User sở hữu đơn.

Reference được lưu và **commit trước khi gọi HTTP**. Lỗi mạng không sinh mã mới cho cùng khoản cước: retry/query tiếp tục dùng mã đã lưu. Khóa đơn rồi khóa thanh toán giữ thứ tự nhất quán; không giữ khóa trong thời gian đợi MoMo. Callback lặp không thu tiền hay ghi PAID lần hai, callback thất bại muộn không hạ trạng thái PAID. Cột `transaction_ref` hiện có lưu `requestId` lúc chờ và `requestId.transId` sau thành công; kể cả ID Long lớn nhất cũng nằm trong 50 ký tự.

Nếu khách hủy đơn rồi MoMo xác nhận tiền đã nhận, vẫn lưu PAID để phản ánh đúng giao dịch; không mở lại đơn. Manager cần đối soát hoàn phí riêng. Chưa tự động hoàn tiền qua API và chưa có quy trình hoàn phí riêng trong lượt tích hợp này.

Nếu tạo giao dịch bị timeout nhưng MoMo đã nhận request, chọn **Kiểm tra kết quả thanh toán** trước khi thử tạo lại. Hệ thống dùng một reference bền vững cho mỗi khoản cước; sau lỗi thất bại cuối cùng không tự sinh giao dịch thứ hai có thể thu tiền trùng. Khả năng tạo lượt mới sau thất bại cuối cùng cần bảng lịch sử attempts/đối soát riêng trong bước nâng cấp.

## Kiểm chứng

Các test offline giả lập HTTP MoMo và kiểm: canonical HMAC tạo/query; IPN sai chữ ký, sai partner/request/order/amount/currency; URL cổng khác; lỗi mạng; owner mismatch; khóa/commit trước HTTP; retry giữ reference; callback lặp; callback muộn; provider transId khác; giới hạn 50 ký tự; miễn cước 0; payload QR không trở thành HTML. Bộ test không gọi dịch vụ thật và không phát sinh thanh toán.

Tài liệu chính thức đối chiếu:

- [Thanh toán ví và QR](https://developers.momo.vn/v3/docs/payment/api/wallet/onetime/).
- [Hosted payment ví/thẻ](https://developers.momo.vn/v3/docs/payment/api/collection-link/).
- [Query trạng thái giao dịch](https://developers.momo.vn/v3/docs/payment/api/payment-api/query/).
