# UTEExpress — rà soát validation, giao diện và backend

Ngày kiểm tra: 12/10/2026. Phạm vi: mã nguồn local `feature_Tho` tại `1ada00d` cùng các thay đổi chưa commit; đối chiếu chọn lọc mã thực tế tại `origin/develop` `99e5f35`.

Mô hình đã chốt: một thương hiệu, nhận/giao toàn quốc qua mạng giao nhận mô phỏng dưới một trung tâm điều phối; 5 tác nhân Guest/User/Shipper/Manager/Admin, 4 role tài khoản. Giảng viên đã đồng ý mô hình logistics và Shipper theo xác nhận của thành viên nhóm.

## 1. Kết quả và giới hạn kiểm tra

- Chạy bộ test hiện có: **182 test, 0 failure, 0 error**, hoàn tất lúc 10:36 ngày 12/10/2026 (giờ Việt Nam). Lệnh: `mvn --offline -Dmaven.repo.local=C:\Users\ADMIN\.m2\repository test`; profile tích hợp Aiven không được bật.
- Chạy thêm 10 kiểm tra auth offline bằng validator, mock repository và Spring transaction interceptor; tái hiện lỗi rollback bộ đếm OTP, thiếu giới hạn dữ liệu và quota địa chỉ.
- Chạy probe tạo đơn, khối lượng, tiền, tệp giả và broker WebSocket độc lập; xác nhận các ca lỗi được mô tả dưới đây. Không gửi thông báo giả lên server đang chạy, không ghi database hoặc gửi email từ các probe.
- Xem trực tiếp trang chủ ở 1280×800 và 390×844; xem danh sách Shipper bằng tài khoản demo ở 390×844. Các màn hình User/Manager/Admin còn lại được rà soát source, chưa có bằng chứng chụp/kiểm tra visual cho mọi màn hình.
- Kết quả test pass phản ánh các tình huống hiện có trong test suite; nhiều ca biên và transaction chưa có test. Không diễn giải 182 test pass thành toàn bộ chức năng đã đúng.
- Chưa kiểm thử SMTP thật, callback của VNPay/MoMo, tải video thực tế, kiểm thử tải hoặc concurrency trên database thật. Các nguy cơ concurrency dưới đây được tách riêng khỏi lỗi đã tái hiện offline.
- Mật khẩu demo `123456` của các tài khoản hiện có được giữ theo yêu cầu của nhóm. Audit không đổi mật khẩu, role hay trạng thái kích hoạt.

Nhật ký test: `target/codex-validation-audit-tests.log`. Probe auth và order nằm trong `target/codex-validation-audit/`; thư mục target không lên Git.

## 2. Những phần đã có nền tảng đúng

| Nhóm | Bằng chứng hiện có | Phần còn phải xác minh/hoàn thiện |
| --- | --- | --- |
| Kiến trúc | Controller → Service → Repository; DTO riêng, JPA/MySQL, layout Bootstrap dùng chung | Hợp nhất validation và transaction ở các service còn thiếu. |
| Xác thực | BCrypt, OTP ngẫu nhiên, hạn dùng, phản hồi reset trung tính | Giới hạn thử/gửi OTP, kích hoạt lại và giới hạn chuỗi/byte. |
| CSRF và role | Local có mặc định CSRF cho POST, role Shipper; test public session và staff pass | Quyền từng đối tượng và STOMP cần kiểm tra riêng; tích hợp develop phải giữ các bảo vệ này. |
| Hồ sơ/địa chỉ | DTO không bind role/id; địa chỉ được truy vấn theo id + owner | Quota khi đổi loại địa chỉ và test các ca biên. |
| Đơn/nháp | Đơn nháp lưu DB; truy vấn owner; tạo mã vận đơn | Transaction tạo trực tiếp, chống submit lặp, kiểm tra dữ liệu và cạnh tranh cập nhật. |
| Shipper | Chỉ xử lý đơn được phân công; whitelist chuyển trạng thái; giao thành công cần ảnh/chữ ký | Kiểm tra thanh toán, bảo vệ file, event history và concurrency. |
| Review | Chủ đơn đã giao mới được đánh giá; 1–5 sao, trim text 50–1.000 ký tự trong DTO local; một review/đơn có unique | Nội dung media, dọn file khi persistence lỗi, và commit phần DTO local còn chưa lên GitHub. |
| Quản trị nội bộ | Role Admin/Manager riêng, chống tự hạ quyền/khóa, kiểm tra phiên và revision | Quản lý khách hàng/Shipper và bảng cước vẫn cần hoàn thiện theo phạm vi. |
| Realtime | Có WebSocket, event gửi sau transaction commit | Phân quyền SEND/SUBSCRIBE và cập nhật nút thao tác trên UI. |
| Cloud | Lượt kiểm tra trước đã xác nhận Aiven MySQL 8.4, TLS, CA và trang chủ HTTP 200 | Lượt audit này dùng test offline, không phải một lần test đầy đủ nghiệp vụ cloud mới. |

## 3. P1 — nên sửa trước đợt demo hoàn chỉnh

### P1-01. Giới hạn nhập sai OTP đổi email không được giữ

Nguồn: [EmailChangeServiceImpl.java](../src/main/java/vn/edu/hcmute/uteexpress/service/impl/EmailChangeServiceImpl.java), class transaction ở dòng 21; xử lý sai/hết hạn ở dòng 90–111.

`confirm()` tăng số lần sai rồi ném `IllegalStateException`; transaction rollback cả bộ đếm. Nhánh xóa OTP hết hạn cũng rollback. Probe qua Spring transaction interceptor ghi nhận 6 lần sai = 0 commit/6 rollback. Unit test gọi service trực tiếp sẽ không phát hiện hành vi transaction này.

Sửa: trả về kết quả kiểm tra để transaction ghi bộ đếm/xóa yêu cầu trước khi controller báo lỗi, hoặc tách cập nhật bộ đếm thành transaction riêng có chủ đích. Giữ rollback cho lỗi cập nhật tài khoản/SMTP. Kiểm thử count thực tế sau từng lần sai, lần thứ 5, mã hết hạn và mã mới.

Quy tắc rollback mặc định với runtime exception được mô tả trong [Spring Framework](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html).

### P1-02. OTP đăng ký/reset chưa giới hạn số lần thử và gửi

Nguồn: [AuthServiceImpl.java](../src/main/java/vn/edu/hcmute/uteexpress/service/impl/AuthServiceImpl.java), dòng 64–77 và 83–138.

Probe sai 100 lần ở từng luồng rồi mã đúng vẫn được chấp nhận. Chưa có bộ đếm/cooldown cho gửi reset. Hạn OTP 10 phút không thay thế giới hạn thử/gửi.

Sửa: giới hạn theo tài khoản và nguồn yêu cầu, lưu số lần thử đúng transaction, cooldown gửi OTP, thông báo trung tính; bổ sung gửi lại OTP kích hoạt. Khi mã kích hoạt hết hạn, luồng hiện tại bị kẹt: đăng ký lại trùng username/email, reset bỏ qua tài khoản chưa kích hoạt.

### P1-03. Client có thể phát thông báo trên topic WebSocket hệ thống

Nguồn: [WebSocketConfig.java](../src/main/java/vn/edu/hcmute/uteexpress/config/WebSocketConfig.java), dòng 21; [tracking-realtime.js](../src/main/resources/static/js/tracking-realtime.js), xử lý sự kiện trạng thái.

Broker local chưa có kiểm soát inbound. Probe broker độc lập với cùng cấu hình xác nhận client `SEND` tới topic vận đơn bất kỳ được chuyển cho subscriber. UI có thể nhận thông báo trạng thái giả; probe này không chứng minh database bị sửa. Subscription cũng cần kiểm tra quan hệ với vận đơn, thay vì chỉ yêu cầu đã đăng nhập ở HTTP.

Sửa: chặn client gửi vào topic/queue hệ thống, kiểm quyền đăng ký theo chủ đơn/Shipper/nội bộ, giới hạn origin phù hợp. `develop` đã có chặn SEND; quyền subscribe topic vận đơn còn cần hoàn thiện.

### P1-04. Ảnh/chữ ký giao hàng có URL công khai

Nguồn: [DeliveryProofStorageServiceImpl.java](../src/main/java/vn/edu/hcmute/uteexpress/service/tracking/impl/DeliveryProofStorageServiceImpl.java), dòng 28; [SecurityConfig.java](../src/main/java/vn/edu/hcmute/uteexpress/config/SecurityConfig.java), dòng 33.

File proof được lưu dưới `/uploads/giao-hang`, còn `/uploads/**` được permitAll và ánh xạ tài nguyên. Biết URL thì không cần là chủ đơn để lấy file; tên UUID không thay thế kiểm quyền. Đây là kết luận từ đường phục vụ file, không có thao tác tải/chia sẻ dữ liệu riêng tư lên bên ngoài.

Sửa: dùng kho file proof riêng và endpoint kiểm người xem. Có thể giữ media review công khai theo quyết định sản phẩm. `develop` đã có phần bảo vệ proof để tích hợp chọn lọc.

### P1-05. Tạo trực tiếp chưa là một giao dịch thống nhất

Nguồn: [OrderServiceImpl.java](../src/main/java/vn/edu/hcmute/uteexpress/service/impl/OrderServiceImpl.java), dòng 76 và 102–107.

Luồng trực tiếp lưu đơn, sau đó tạo payment và tăng lượt mã khuyến mãi; method không có transaction bao ngoài. Lỗi ở bước sau có thể còn đơn thiếu payment. Đây là kết luận từ ranh giới transaction; chưa cố tình tạo lỗi trên database cloud. Luồng xác nhận giỏ có transaction bao ngoài, không nên quy lỗi này cho mọi cách tạo đơn.

Sửa: tích hợp transaction tạo đơn đã có ở `develop`; kiểm thử rollback đơn/payment/khuyến mãi cùng lúc. Cần khóa/atomic update riêng cho quota khuyến mãi khi concurrency.

## 4. P2 — validation và tính nhất quán nghiệp vụ

| ID | Phát hiện | Bằng chứng | Hướng sửa |
| --- | --- | --- | --- |
| V01 | Đăng ký nhận password 1 ký tự, username 51 và fullName 101 | Validator chấp nhận [RegisterForm](../src/main/java/vn/edu/hcmute/uteexpress/controller/auth/AuthController.java) dòng 128–139; entity giới hạn username 50/tên 100 | Size/Pattern và normalize thống nhất; giới hạn khớp schema; phản hồi unique conflict rõ ràng. |
| V02 | Public password chưa giới hạn byte BCrypt | Probe thư viện local xác nhận hai chuỗi khác nhau sau byte 72 cùng matches một hash; DTO hiện thiếu cap | Giới hạn ký tự theo chính sách và tối đa 72 byte UTF-8 trước encoder ở đăng ký/đổi/reset. Staff đã có kiểm tra byte. |
| V03 | Điện thoại/tên/địa chỉ tạo đơn chỉ kiểm không trống | [OrderCreateRequest](../src/main/java/vn/edu/hcmute/uteexpress/dto/OrderCreateRequest.java) chấp nhận `abc`, tên 101, địa chỉ 256 dù vượt giới hạn cột | Regex điện thoại theo chính sách, trim và max length khớp DB; validate ở server. |
| V04 | Trọng lượng tạo đơn nhận 1.000 kg và Infinity | Probe validator không báo lỗi; Infinity làm bộ tính cước ném NumberFormatException. NaN/âm đã bị từ chối | Finite + min/max/độ chính xác, đồng nhất với tính cước đang giới hạn 100 kg; kiểm tra thêm ở service. |
| V05 | Cước có số lẻ nhưng DB lưu VND nguyên | 0,0001 kg tính 15000.30000; order/payment có scale 0 | Chốt bước cân và làm tròn cước/giảm giá thống nhất trước hiển thị/lưu/thanh toán. |
| V06 | Đổi loại địa chỉ bỏ qua quota 20 | Probe sửa SENDER sang RECEIVER đang có 20 vẫn save; [SavedAddressServiceImpl](../src/main/java/vn/edu/hcmute/uteexpress/service/impl/SavedAddressServiceImpl.java) dòng 104–120 | Kiểm quota nhóm đích khi đổi loại, test count 19/20. |
| V07 | Media đánh giá chỉ kiểm đuôi | [FileStorageServiceImpl](../src/main/java/vn/edu/hcmute/uteexpress/service/impl/FileStorageServiceImpl.java) dòng 60: fake.png với MIME text/html và bytes HTML được nhận IMAGE | Kiểm signature/nội dung, decode ảnh có giới hạn, kiểm video phù hợp; không suy diễn thành XSS/RCE khi chưa kiểm browser/headers. |
| V08 | Ví ghi phải trả trước nhưng giao thành công chưa kiểm PAID | [OrderPayment](../src/main/java/vn/edu/hcmute/uteexpress/entity/OrderPayment.java) dòng 75 và [TrackingServiceImpl](../src/main/java/vn/edu/hcmute/uteexpress/service/tracking/impl/TrackingServiceImpl.java) dòng 87 | Chốt chính sách; nếu trả trước thì chặn bước lấy/giao theo quy định khi UNPAID, bổ sung test. |
| V09 | Quota khuyến mãi có nguy cơ vượt/lost update | `requireUsableCode` và `markUsed` đọc/tăng không lock/version, [PromoCodeServiceImpl](../src/main/java/vn/edu/hcmute/uteexpress/service/impl/PromoCodeServiceImpl.java) dòng 43/89 | Khóa trong transaction tạo đơn hoặc atomic update có điều kiện; chưa stress DB nên ghi đây là nguy cơ concurrency. |
| V10 | Submit lặp tạo trực tiếp có thể sinh hai đơn | Mỗi POST sinh mã mới, chưa có idempotency | Khóa yêu cầu bằng token/key, trả lại kết quả đã tạo; vô hiệu hóa nút chỉ giúp UX, không thay thế server. |
| V11 | Hủy và Shipper lấy hàng chưa khóa cùng bản ghi ở local | Code kiểm trạng thái rồi save, không cùng lock/version | Tích hợp lock đã có trên develop; bổ sung test hai thao tác cạnh tranh. Chưa tái hiện trên cloud. |

Khả năng dùng OTP đồng thời hoặc xác nhận cùng đơn nháp cần kiểm thử transaction/concurrency riêng. Không khẳng định đơn nháp đã nhân đôi: xóa cùng bản ghi có thể khiến transaction thứ hai rollback.

Nguyên tắc đối chiếu: kiểm cú pháp và ý nghĩa nghiệp vụ trên server, có giới hạn chuỗi/số, và kiểm quyền riêng cho từng đối tượng. [OWASP Input Validation](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html), [OWASP Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html). Tên và MIME gửi lên chỉ là metadata; kiểm nội dung, kích thước, nơi lưu và quyền phục vụ tệp theo [OWASP File Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html).

## 5. Giao diện — quan sát và nâng cấp phù hợp phạm vi

Quan sát trực tiếp:

- Trang chủ desktop 1280×800 có bố cục thống nhất, CTA/tính năng dễ nhận diện; ở 390×844 các khối xếp dọc, không bị cuộn ngang toàn trang.
- Shipper mobile: bảng 8 cột rộng khoảng 873 px trong viewport 390 px. Các cột trạng thái/thao tác ở ngoài vùng đang nhìn thấy; 20 đơn khiến trang kéo dài. Đây là vấn đề thao tác, dù bảng đã có vùng cuộn riêng.
- Thanh tài khoản Shipper mobile chật; nút đăng xuất bị che một phần ở mép phải. Cần bố trí lại tài khoản/logout trong menu.

| Màn hình | Nâng cấp cụ thể | Căn cứ |
| --- | --- | --- |
| Tạo đơn | Chia thông tin lấy/giao → kiện/dịch vụ → kiểm tra cước/xác nhận; giữ đơn nháp; địa chỉ vùng phục vụ rõ | [order-form.html](../src/main/resources/templates/user/order-form.html) thiếu required/min/max/maxlength/inputmode và lỗi riêng dịch vụ. |
| Form chung | Lỗi tại ô + bản tổng hợp, giữ dữ liệu hợp lệ, focus lỗi đầu, label gắn đúng id, autocomplete/inputmode | Đăng ký/login thiếu for/id; review label for không khớp textarea; OTP thiếu gợi ý bàn phím số. |
| Guest | CTA Tạo đơn dẫn đúng trang theo trạng thái đăng nhập; mobile có menu tra cứu/tính cước | [index.html](../src/main/resources/templates/index.html) CTA luôn sang đăng ký; public nav ẩn khi màn hình nhỏ. |
| Dịch vụ/cước | Hiển thị vùng áp dụng và ETA theo tuyến; dùng dữ liệu cấu hình | [uoc-tinh-cuoc.html](../src/main/resources/templates/uoc-tinh-cuoc.html) ghi hỏa tốc trong ngày nhưng chưa có tuyến/vùng. |
| Shipper | Thẻ đơn trên điện thoại, tab trạng thái, tìm mã, phân trang; nút gọi và thao tác chính rõ | Bảng 8 cột đã quan sát; số điện thoại chi tiết hiện là text thuần. |
| Realtime | Đồng bộ khu nút thao tác hoặc thông báo cần làm mới khi trạng thái đổi | JS hiện đổi badge/message nhưng nút vẫn từ trạng thái lúc tải trang. |
| Manager | Khu điều phối gồm đơn chờ, Shipper/khu vực và lịch sử xử lý; bộ lọc có trạng thái rỗng/lỗi rõ | Bám vai trò trung tâm điều phối toàn quốc mô phỏng. |
| Admin | Tách quản lý tài khoản, dịch vụ/cước/vùng, khuyến mãi và nhật ký; cùng layout chung | Bám phạm vi quản trị hệ thống, tránh nhập nhằng với điều phối hàng ngày. |

Lỗi form nên ngắn, có cách sửa, liên kết với input và thông báo cho công cụ hỗ trợ theo [W3C WAI — User Notifications](https://www.w3.org/WAI/tutorials/forms/notifications/).

Giữ nền tảng Spring Boot/Thymeleaf/Bootstrap/MySQL hiện tại, thống nhất component form/badge/table/empty state và thiết kế thao tác theo từng role. Phần giao diện mới của develop đã có shared auth, CSS chung và workspace Shipper; tích hợp trước khi xây lại các phần tương tự.

## 6. Thứ tự nâng cấp backend

1. **P1 trước:** OTP/transaction bộ đếm, giới hạn thử/gửi, phân quyền STOMP, private proof, transaction tạo đơn. Mỗi lỗi có test hồi quy ở đúng tầng, gồm Spring proxy hoặc broker thực khi cần.
2. **Validation chung:** DTO khớp schema, normalize, finite/range, giới hạn byte mật khẩu, phone, quota chuyển loại địa chỉ, media thật, phản hồi lỗi. Áp dụng giống nhau ở form tạo trực tiếp và đơn nháp; service vẫn kiểm business invariant.
3. **Nghiệp vụ logistics:** bảng cước theo dịch vụ/vùng/cân nặng, cước chốt trên đơn, trạng thái/lý do/sự kiện, khu vực phân công, chống submit lặp và khuyến mãi concurrency.
4. **Tiền và thanh toán:** chốt thu cước hay thêm thu hộ tiền hàng, trạng thái ví trả trước, một cổng sandbox nếu cần; callback phải kiểm nguồn/chữ ký/số tiền/đơn và xử lý lặp đúng một lần.
5. **UI và hồ sơ demo:** cải thiện mobile Shipper, form lỗi, điều phối Manager; báo cáo chức năng thực tế và bộ testcase đi kèm.

VNPay/MoMo hiện mô phỏng, COD hiện thu cước; đây là giới hạn chức năng phải ghi rõ. JWT còn stub; WebSocket đã có thể làm chức năng sáng tạo khi quyền và tính đúng được hoàn thiện.

## 7. Tích hợp develop có chọn lọc

Mã develop đã có transaction createOrder, lock hủy/lấy hàng, event history, giao lại, phân công, private proof, đối soát thu cước và nhiều UI mới. Giá cước vẫn gán trong code; quyền subscribe vận đơn và quota khuyến mãi còn phải xem xét.

SecurityConfig ở develop có khác biệt so với local đã pass test: thay CSRF từ mặc định sang các matcher cụ thể và cho phép manager/admin trong chain chung. Chain staff ưu tiên cao vẫn bảo vệ các URL đó khi được nạp, nhưng phải giữ hành vi fail-closed nếu chain staff không có. Không ghi đè nguyên cấu hình; đối chiếu và chạy lại test cho mọi POST/role sau tích hợp.

## 8. Ma trận kiểm thử cần bổ sung

| Nhóm | Ca kiểm thử tối thiểu |
| --- | --- |
| Registration/profile | Rỗng/trắng; độ dài max/max+1; Unicode; phone sai; username/email trùng và chuẩn hóa; role/id chèn thêm không được bind. |
| Password | Min-1/min; max/max+1; đúng 72/73 byte UTF-8; confirm mismatch; current sai; giữ đăng nhập demo 123456. |
| OTP | Đúng/sai/hết hạn/dùng lại; lần thử 4/5/6; counter được commit sau lỗi; gửi lại/cooldown; kích hoạt lại sau expiry; concurrency riêng. |
| Ownership/roles | Guest/User khác/Shipper khác/Manager/Admin với cùng URL và POST; sửa id/owner; private proof; các điểm CSRF. |
| Order/draft | Chuỗi quá dài; phone; weight null/0/âm/NaN/Infinity/max/max+1; enum sai; vùng không hỗ trợ; submit lặp; lỗi bước payment gây rollback. |
| Status/dispatch | Chuyển hợp lệ/sai; hủy đúng giai đoạn; shipper không được phân công; proof thiếu; ví chưa trả; hủy/lấy đồng thời. |
| Pricing/promo | VND rounding thống nhất; mã hết hạn/tắt/hết lượt; giảm vượt cước; hai request nhận lượt cuối; giá đơn cũ không đổi. |
| Payment | Sai order/số tiền/chữ ký; callback lặp hoặc lệch trạng thái; COD/cước/thu hộ theo chính sách; simulator không bị báo là gateway thật. |
| Upload | Tệp quá dung lượng; đuôi/MIME/bytes không khớp; ảnh quá nhiều pixel; video lỗi; filename/path bất thường; file được dọn nếu DB rollback; quyền đọc. |
| Realtime | Client SEND topic bị chặn; subscribe sai owner bị chặn; event chỉ sau commit; nút thao tác UI khớp trạng thái mới. |
| Address | Owner khác; mặc định mỗi nhóm; create quota19/20; update chuyển nhóm đang đầy; nhãn trùng và dữ liệu sai. |
| Responsive | Home/auth/create/tracking/dashboards cả 4 role ở desktop/mobile; bàn phím; focus/lỗi; chuỗi dài; bảng rỗng/nhiều dòng; loading/offline. |

Các hàng trong ma trận là kế hoạch hoàn thiện kiểm thử, không phải danh sách đã chạy hết. Dùng testcase và kết quả thật để báo cáo mức đáp ứng 6/10 điểm về logic, validation và security.
