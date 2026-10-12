# UTEExpress — bản sửa và kiểm chứng 12/10/2026

Tham khảo project rạp `D:/CNPM_NHOM8` ở chế độ chỉ đọc. Thành viên đã cho phép dùng lại bộ cấu hình MoMo TEST; các khóa chỉ nằm trong file local bị Git bỏ qua. Project rạp không được chỉnh sửa.

## Thanh toán

- Gọi MoMo TEST để tạo QR (`captureWallet`) hoặc trang ví/thẻ (`payWithMethod`); kiểm HMAC-SHA256, partner/request/order/amount và quyền người gửi.
- Commit reference trước HTTP; timeout giữ cùng mã. URL quay về/nút xác nhận không tự ghi PAID. Callback lặp không ghi lần hai; lỗi muộn không hạ PAID; tiền đến sau hủy vẫn được ghi để đối soát.
- Cước 0 được miễn bởi tính toán máy chủ; VNPay chưa được cung cấp như một lựa chọn hoạt động.
- Kiểm tra trực tiếp: MoMo tạo QR cho vận đơn mẫu UTE250011, cước53.200đ; query trả chờ thanh toán. Chưa hoàn tất bằng app/thẻ và không có tiền thật phát sinh.
- Localhost không nhận IPN từ Internet; query máy chủ vẫn hoạt động. Xem [hướng dẫn MoMo](MOMO_TEST_PAYMENT.md).

## Các vấn đề đã sửa

| Nhóm | Bản sửa |
| --- | --- |
| OTP/email | Bộ đếm sai được commit đúng transaction; giới hạn5 lần/cooldown/nguồn; vô hiệu mã ở lần5; gửi lại kích hoạt hết hạn |
| Dữ liệu | Chuỗi khớp schema, normalize, giới hạn72 byte BCrypt; phone; cân hữu hạn0,1–100kg, tối đa3 số lẻ |
| Giao dịch | Atomic create order/payment/promo; khóa quota mã, order→payment, owner→draft; hủy/lấy hàng dùng lock |
| Gửi lặp | Token session gắn account, ghi nhớ orderId, redirect và trả lại cùng đơn khi gửi lại |
| WebSocket | Chặn client SEND; topic canonical; kiểm owner/Shipper/nội bộ, enabled/phiên/đổi mật khẩu và origin |
| Tệp | Proof có endpoint kiểm quyền; không public URL trực tiếp; kiểm ảnh thật/pixel/container video, dọn file lỗi/rollback, xóa proof cũ saucommit |
| Thanh toán/giao | Chặn ví chưa PAID trước lấy/giao/lưu proof; VND rounding thống nhất; signedIPN/query, idempotent ledger |
| Lỗi | 400/403/404/409 phù hợp; lỗi500 trung tính, không đưa chi tiết exception lên màn hình |
| UI | Menu/CTA theo role; Shipper mobilecard/filter/tel; giao lại; realtime khóa nút cũ; label/giới hạn/input/error aria/focus; ETA theo tuyến |

## Bằng chứng chạy

1. **334 test**, 0 failure/error; có test Spring transaction proxy, mockHTTP MoMo, policy/interceptor, lifecycle tệp thật, DTO và idempotency.
2. Đóng gói thành công; JAR không chứa cấu hình local/cloud thật, aiven-local, CA hay truststore/khóa.
3. Server mới ở localhost8080, kết nối Aiven qua TLS và validate schema thành công; không khởi tạo lại schema.
4. 11 tài khoản vẫn có hash khớp123456; 10 tài khoản enabled đăng nhập đúng role, chuakichhoat bị từ chối.
5. **12 kiểm tra HTTP thực** pass: login; cân Infinity/NaN/1000/âm; token; phương thức MoMo/VNPay; phone abc; proof public/private; IPN giả; resend thiếuCSRF. Các kiểm tra này không tạo đơn/gửi email.
6. Tạo QR và query MoMo TEST trực tiếp thành công, payment vẫn chưa thanh toán. Ảnh minh chứng ở `target/fix-evidence/momo-test-qr.jpg`, được Git bỏ qua.

Log/probe nằm trong target: `codex-fixes-tests.log`, `codex-fixes-package.log`, `codex-runtime-smoke.log`, `CodexRuntimeSmoke.java`.

## Giới hạn cần ghi rõ

- Bộ đếm nguồn/public OTP/login nằm trong bộ nhớ một instance, reset khi restart; mã dùng hết lần được vô hiệu ở DB. Nhiều instance cần rate-limit dùng chung.
- Upload mới hỗ trợ JPG/JPEG/PNG/GIF và container MP4/WebM; chưa có decoder WebP hoặc pipeline kiểm codec/transcode video đầy đủ.
- Một reference cho một khoản cước; tạo lượt mới sau thất bại cuối cùng, phục hồi checkout qua phiên khác và hoàn phí tự động cần lịch sử attempts/đối soát riêng. Không tự tạo lượt mới có thể thu tiền trùng.
- COD là thu cước. Thu hộ tiền hàng, bảng cước theo vùng trong DB, lịch sử vận đơn đầy đủ và quản trị toàn bộ khách hàng/Shipper là các phần nâng cấp nghiệp vụ riêng, không được ghi là đã hoàn thành bởi bản sửa validation này.
- Chưa stress MySQL concurrent, gửi SMTP thật hay hoàn tất app/thẻ TEST trong lượt kiểm chứng. Test proxy/mock chứng minh ranh giới/logic, không thay thế các phép thử đó.

[Phạm vi](PROJECT_SCOPE_2026-10-12.md) · [Audit gốc](VALIDATION_UI_BACKEND_AUDIT_2026-10-12.md) · [Tài khoản demo](DEMO_ACCOUNTS.md)
