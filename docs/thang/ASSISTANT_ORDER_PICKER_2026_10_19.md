# Nâng cấp chatbot: gợi ý cố định và chọn đơn theo tài khoản

## Đồng bộ code

Đã fetch và fast-forward `origin/develop` trực tiếp vào `E:\Lap_Trinh_Web\UTEEXPRESS`, từ `c53e7c7` đến `3bd316d` (6 commit), vẫn ở `feature_Thang`. Giữ các thay đổi local; ghép lại hàm tra cứu chatbot trong OrderRepository cùng các hàm thống kê mới của nhóm. Không stash, reset, xóa code hoặc tạo commit/push thay người dùng.

Không chạy migration/seed hoặc ghi dữ liệu nghiệp vụ lên Aiven. Code profile/avatar mới từ develop được giữ nguyên. Không thay application.properties, README, .project hoặc file backup cũ trong đợt chatbot này.

## Giao diện

- Dải gợi ý cuộn ngang luôn nằm trên ô nhập, không biến mất sau câu trả lời, thử lại hay xóa hội thoại. User có 6 gợi ý; Shipper có 5 gợi ý phù hợp vai trò. Bấm gợi ý gửi câu hỏi ngay, khóa gửi khi đang xử lý.
- Có thể hỏi câu khác sau lượt lỗi mà vẫn giữ lịch sử. Giới hạn tài khoản/provider vẫn áp dụng ở backend; không tự retry AI.
- Nút “Chọn đơn của tôi” tải danh sách từ backend, tối đa 10 đơn/trang, có trước/sau và tải lại. Nhãn User: “Đơn do tôi tạo”; Shipper: “Đơn được phân công cho tôi”. Không phải nhập mã thủ công.
- Câu hỏi cần xác định đơn có thể vào trạng thái `NEEDS_ORDER`: bộ chọn mở để chọn, rồi tiếp tục đúng câu hỏi/requestId cũ, không thêm bản sao. Nếu tài khoản chỉ có một đơn thì backend tự nhận diện; tài khoản rỗng được gửi ngữ cảnh `NO_ORDERS` để AI không suy đoán.
- Đơn được chọn hiển thị rõ ở phía trên gợi ý và có nút bỏ chọn. Bỏ chọn cho phép hỏi chung mà không giữ ngầm đơn trước. Câu hỏi tiếp nối dùng lựa chọn hiện trên giao diện, vẫn đọc lại dữ liệu mỗi lượt.
- Bộ chọn là popover trong chat, không làm ô nhập/nút Gửi tràn khung 1280×720. Escape đóng bộ chọn trước, Escape tiếp đóng chat.

## Ràng buộc dữ liệu

Chatbot vẫn hỗ trợ hướng dẫn, trạng thái, lịch sử, thanh toán, điều kiện hủy/giao lại, cước và công việc; không loại bỏ chức năng để thay cho việc phân quyền.

1. Danh tính chỉ từ PublicAccountPrincipal/phiên đã xác thực; kiểm tra tài khoản còn hoạt động, role, accessVersion và mật khẩu. Không nhận role/username/userId từ browser/AI.
2. `GET /api/assistant/orders`: User query theo người gửi, Shipper query theo người được phân công; refresh từng bản ghi và đối chiếu lại ID tài khoản. Trả chỉ mã, trạng thái, dịch vụ và ngày tạo, không gửi toàn bộ entity hay danh tính khác.
3. API chọn đơn chỉ nhận page/query, không nhận tham số danh tính hoặc tham số lặp. Trang 0–1000; query tối đa 20 ký tự chữ/số/gạch ngang. Có thể dùng API lọc mã nếu cần, nhưng UI mặc định chọn từ danh sách, không yêu cầu nhập.
4. `selectedOrderCode` chỉ là lựa chọn, **không phải giấy phép**. Backend tra cứu lại theo account/role trước khi gọi Groq. Đơn của người khác và đơn không tồn tại dùng cùng lỗi `ORDER_NOT_AVAILABLE`, không tiết lộ nội dung hoặc xác nhận tồn tại.
5. Lúc tiếp tục/thử lại vẫn kiểm tra quyền và dữ liệu mới. Nếu Shipper bị gỡ phân công trong lúc AI xử lý, câu trả lời bị bỏ và yêu cầu chọn lại. Nếu trạng thái/thanh toán/lịch sử điều kiện đã đổi, trả `ORDER_CHANGED` để thử lại, không trả câu trả lời dựa trên dữ liệu cũ.
6. UUID hội thoại tách theo account. User B không thể dùng UUID hội thoại hoặc lựa chọn của User A. RequestId gắn câu hỏi/lựa chọn; không cho sửa ngầm lựa chọn của một request đã hoàn tất sang đơn khác.
7. Khi đổi account/logout, history/selection/list bị xóa ở client; response chat/danh sách đến muộn bị bỏ qua bằng generation + conversationId + role. Không dùng browser storage lưu chat. Server tiếp tục phân quyền độc lập với JS.
8. JSON đọc nghiêm kiểu dữ liệu, từ chối key trùng, field lạ, UUID sai, selectedOrderCode sai, question không phải chuỗi, retry không phải boolean. Giữ giới hạn 16 KiB/1200 ký tự, CSRF và text-only chống XSS.

Không có tool hủy/sửa đơn, thanh toán, cập nhật tài khoản, SQL hoặc shell. Tra cứu dữ liệu cá nhân/ghi chú tự do không được đưa nguyên entity lên Groq. Các link chi tiết vẫn đi qua phân quyền hiện có của website.

## Kiểm thử

```powershell
mvn '-Dtest=*,!Aiven*IntegrationTest' test
node --test src/test/js/staff-navigation.test.cjs src/test/js/tracking-realtime.test.cjs src/test/js/assistant-chat.test.cjs src/test/js/assistant-widget.test.cjs
```

Kết quả lần cuối: **731 test Java và 37 test JavaScript qua, không lỗi/skip**. Java gồm 56 ca chatbot (26 integration H2, 12 hội thoại, 16 gateway, 2 rate guard); JS gồm 16 ca chatbot/client/render và 21 ca hồi quy cũ. Các test Aiven ghi dữ liệu QA vẫn loại khỏi lệnh. Test context/SELECT Aiven sẵn có đã qua, không sửa dữ liệu cloud.

Ca mới: danh sách User/Shipper đúng phạm vi, paging/filter/input bất thường, nhiều đơn/1 đơn/không có đơn, AI yêu cầu picker, chọn và resume một request, fake role/userId/username, chọn mã người khác, User B dùng hội thoại A, gỡ phân công hoặc đổi trạng thái trong lúc chờ, JSON duplicate/type, request async account switch, gợi ý còn sau reply/clear, XSS text-only.

Đã dùng computer-use kiểm tra UI fixture riêng của hai vai trò ở breakpoint desktop 1280×720; kiểm tra light/dark, picker, gợi ý ngang phía trên textarea, gợi ý còn sau câu trả lời, nút Gửi nằm trong viewport. Ảnh kiểm tra trong `target/assistant-user-order-picker.png` và `target/assistant-suggestions-after-answer.png` (không commit target). Skill này chỉ dùng cho kiểm tra giao diện, không tác động dữ liệu thật.

Fixture ở src/test/js/assistant-preview-server.cjs dùng HTML render từ H2 và dữ liệu giả có nhãn UI TEST; không chứng minh nội dung trả lời Groq thực. Groq API/tool calling đã được kiểm tra ở đợt trước bằng CheckGroqConnection; đợt nâng cấp không gửi thông tin đơn Aiven sang Groq để test. Không thể cam kết mô hình luôn hiểu mọi cách diễn đạt; câu hỏi tự do dùng tool selection, các gợi ý về đơn có bước chọn ở backend.

## Cách dùng

Khởi động lại Spring Boot và Ctrl+F5 trình duyệt để lấy JS/CSS mới. Đăng nhập User hoặc Shipper → mở Trợ lý AI → bấm gợi ý. Khi cần đơn, bấm chọn một đơn sẵn có; câu hỏi đang chờ tiếp tục tự động. Muốn hỏi đơn khác: Chọn đơn của tôi → chọn lại, rồi bấm gợi ý/nhập câu mới. Hỏi đóng gói/cước/quy định chung không bắt buộc chọn đơn.

Giới hạn còn lại: danh sách phân trang tối đa trang 1000; lịch sử/rate-limit vẫn ở RAM theo phiên của một server, mất khi restart. Thông tin gửi ra là ảnh chụp ở thời điểm kiểm tra; không thể loại bỏ mọi thay đổi xảy ra sau khi response đã đến browser. Không công bố “không còn lỗi tuyệt đối” chỉ dựa vào test.

## Nội dung commit riêng

1. `feat(assistant): chon don theo tai khoan va rang buoc du lieu` — DTO lựa chọn/paging, strict JSON, API orders, tools theo role/account, NEEDS_ORDER/resume, kiểm tra lại scope và dữ liệu sau khi AI chạy, test Java.
2. `feat(assistant-ui): giu goi y va chon don khong nhap ma` — gợi ý luôn hiện/bấm gửi ngay, picker phân trang, selection và async isolation ở JS, template/CSS, test client/render, cập nhật fixture và tài liệu.

Một số file backend nền từ đợt trước vẫn chưa được Git theo dõi tại máy hiện tại (AssistantAccessService, AssistantToolService/Impl, ChatConversationResponse, guide và helper/query OrderService/Repository). Khi commit cần thêm đủ chúng; không chỉ commit file mới của picker rồi bỏ sót dependency. Không dùng `git add .` vì còn cấu hình local và thay đổi cũ của người dùng.
