# MoMo mô phỏng riêng trên Render — 19/10/2026

## Phạm vi và trạng thái

- UTEExpress chính vẫn chạy local bằng `UteexpressApplication`. Chỉ `MomoSimulatorApplication` được đóng gói để deploy Render.
- Hai ứng dụng dùng đúng database MySQL Aiven chung. Không tạo database local, không thay đổi schema và không seed đơn/tài khoản trên cloud cho các kiểm thử thanh toán.
- Đã có source, Dockerfile, Blueprint và cấu hình mẫu. Chưa có URL Render được xác nhận hoạt động: cần kết nối Render, commit/push source rồi triển khai. Không sử dụng URL giả để tạo QR cho người dùng.
- File `config/momo-local.properties` đã được tạo riêng và được `.gitignore` bảo vệ. Khóa ngẫu nhiên được chuẩn bị bằng script; không gửi khóa, mật khẩu Aiven hoặc nội dung file cấu hình vào chat/Git.

## Thay đổi chức năng

1. Chỉ chọn COD hoặc MoMo mô phỏng khi tạo đơn/đơn nháp. Backend/DTO đều từ chối VNPay mới. Giữ enum VNPay để đọc lịch sử; đơn VNPay cũ chưa thanh toán, chưa kết thúc có thao tác chuyển sang MoMo, kiểm tra chủ sở hữu và CSRF. Đơn nháp VNPay cũ báo cần tạo lại, không tự đổi lựa chọn khách hàng.
2. Trang thanh toán local hiển thị cước, giảm giá, tổng tiền, QR và liên kết mở web Render. Bỏ endpoint xác nhận thanh toán trực tiếp tại local.
3. QR chứa đúng URL HTTPS `*.onrender.com` được cấu hình, kèm vé AES-256-GCM có hạn 10 phút. Vé ràng buộc đơn, bản ghi thanh toán, chủ đơn, phiên bản quyền, dấu vân tay mật khẩu và số tiền. QR là liên kết có quyền truy cập thanh toán một đơn cụ thể; không chia sẻ công khai.
4. Render đọc tên người gửi/nhận, điện thoại đã che, dịch vụ, khối lượng và chi tiết cước từ Aiven. Không nhận số tiền, chủ đơn hay trạng thái do trình duyệt gửi. Không yêu cầu mật khẩu ví, OTP, thẻ hoặc chuyển tiền thật.
5. Xác nhận mô phỏng có CSRF và xác nhận nhận thức đây là demo. Server khóa cùng hàng `shipment_order` với hủy/điều phối, kiểm tra lại tài khoản/vé sau khi chờ khóa, kiểm tra cước và giảm giá khớp. PAID/biên nhận chỉ được hiển thị sau giao dịch database. Mã giao dịch có tiền tố `SIM-MOMO-` và UUID.
6. Xác nhận lặp/đồng thời trả lại cùng biên nhận, không ghi giao dịch lần hai. Đơn đã hủy, hoàn, giao xong hoặc tài khoản bị khóa/đổi quyền/mật khẩu/thu hồi phiên không thể thanh toán bằng vé cũ. Biên nhận đã trả được đọc lại trong thời hạn vé, kể cả đơn sau đó giao xong.
7. Local đọc trạng thái trực tiếp từ Aiven sau mỗi 3 giây khi tab đang hiển thị và có mạng; không chồng request. Tạm dừng tab ẩn/offline; lỗi kết nối dùng backoff tối đa 15 giây và timeout 8 giây. Dừng tự kiểm tra khi đã trả, đơn đóng, mất quyền hoặc QR hết hạn; có kiểm tra lại/tạo QR mới. Không căn cứ `success=true` hoặc thông báo từ trình duyệt để đánh dấu PAID.
8. Hủy phiên demo chỉ đóng phiên, không tự hủy vận đơn hoặc sửa PAID thành UNPAID. Mất mạng không đồng nghĩa thất bại và cũng không được coi là thành công: kiểm tra kết quả ở UTEExpress trước khi thử lại.
9. Giao diện Render hai cột trên desktop, một cột trên điện thoại; có nhãn mô phỏng, hạn phiên, chi tiết đơn và caption “Bản quyền thuộc Nhóm 8 · Lập trình Web · HCMUTE”. Đã xem bản render từ test trong trình duyệt ở desktop và khung điện thoại 390 px; không dùng đơn thật để chụp UI.

## Cấu hình và deploy

### Chỉ cần một lần Run Spring Boot App để dùng web local

Trong IDE chọn **UteexpressApplication → Run As → Spring Boot App**. Không chạy `MomoSimulatorApplication` trên máy; class đó dành cho service Render.

- Aiven là database cloud và được kết nối/xác minh khi Spring khởi động; không chạy MySQL local.
- Các controller, service, JWT, WebSocket, giao diện và client Cloudinary/Groq của web local do Spring khởi tạo. Cloudinary/Groq là dịch vụ bên ngoài, được gọi khi dùng chức năng tương ứng, không phải chương trình cần mở thêm trên máy.
- Sau `ApplicationReadyEvent`, web local kiểm tra cấu hình URL/khóa MoMo rồi gửi GET `/healthz` để đánh thức Render ở worker nền. Tối đa 3 lượt, timeout 20 giây/lượt và nghỉ 30 giây giữa các lượt; không chặn quá trình mở web và không phải vòng lặp giữ dịch vụ luôn thức. Tắt Spring sẽ hủy worker/request đang đợi.
- Trạng thái `NOT_CONFIGURED`, `CHECKING`, `ONLINE`, `UNREACHABLE` được ghi log và hiển thị ở trang thanh toán; chu kỳ đọc thanh toán 3 giây đồng thời cập nhật trạng thái này từ bộ nhớ, không gọi Render thêm mỗi 3 giây. `ONLINE` chỉ có nghĩa endpoint sức khỏe đã phản hồi trong lượt kiểm tra khởi động, không chứng minh khóa/database giữa hai app đã đồng bộ và không xác nhận đã thanh toán.
- Thiếu URL/khóa hoặc Render lỗi không được báo sẵn sàng và không tự ghi PAID. COD và web local vẫn hoạt động. Các test H2 profile `isolated-logistics` không gửi request ra Render.
- **Vẫn phải deploy Render và điền cấu hình đúng một lần trước.** Run local không thể tự tạo service chưa có hoặc tự deploy các file chưa push lên Git. Sau khi deploy xong, mỗi lần dùng chỉ cần Run web local; khi dừng web local, Render không bị tắt theo.

Có thể tắt kiểm tra khi khởi động bằng `app.momo.startup-check.enabled=false` trong file MoMo private. Không đưa API key quản trị Render vào source hoặc cơ chế Run local.

### Trên máy local

Mở `E:\Lap_Trinh_Web\UTEEXPRESS\config\momo-local.properties`:

```properties
app.momo.simulator-url=https://URL-THUC-TE-CUA-SERVICE.onrender.com
app.momo.sim-secret=KHOA_BASE64_RIENG_32_BYTE
```

Khóa local và biến `MOMO_SIM_SECRET` trên Render phải giống nhau, không dùng mật khẩu database/tài khoản làm khóa. Nếu cần chuẩn bị file lần đầu:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\momo\prepare-local-config.ps1
```

Script giữ nguyên khóa đã có. Sau khi điền URL thực tế, khởi động lại Spring Boot local. Nếu thiếu cấu hình, trang MoMo báo chưa cấu hình; không tạo QR trỏ localhost hay đánh dấu trả tiền giả.

### Trên Render

1. Push source lên `feature_Thang` trước. Tạo Web Service từ repository nhóm bằng Docker, hoặc Blueprint `render.yaml`.
2. Dockerfile: `deploy/momo/Dockerfile`; Docker context: `.`; region Singapore; plan Free; health check `/healthz`. Không dùng lệnh chạy `UteexpressApplication` cho service này.
3. Điền các biến trong phần Environment của Render (không điền giá trị thật vào `render.yaml`):

| Biến | Nguồn |
| --- | --- |
| `MOMO_SIMULATOR_URL` | URL HTTPS thực tế Render cấp cho service; cùng URL điền trên local |
| `MOMO_SIM_SECRET` | Giá trị khóa trong file MoMo private trên máy |
| `AIVEN_HOST`, `AIVEN_PORT`, `AIVEN_DATABASE`, `AIVEN_USER`, `AIVEN_PASSWORD` | Cùng dịch vụ/database trong `config/aiven-local.properties` của nhóm |
| `AIVEN_CA_PEM` | Toàn bộ nội dung CA PEM từ Aiven, gồm BEGIN/END CERTIFICATE; không phải đường dẫn Windows |

Có thể dùng Render Secret File thay cho `AIVEN_CA_PEM`: tải CA lên tên `aiven-ca.pem`, đặt `AIVEN_CA_FILE=/etc/secrets/aiven-ca.pem`. CA được chuyển thành truststore tạm trong runtime chỉ để xác minh TLS, không phải lưu ảnh/tệp nghiệp vụ local. Kết nối bắt buộc `VERIFY_IDENTITY`, không fallback database/truststore hệ thống. Có thể cấp user database riêng cho simulator với quyền tối thiểu, không dùng quyền quản trị lâu dài khi đưa ra môi trường thật.

4. Deploy, kiểm tra `/healthz` trả `ok` và trang `/` ghi rõ mô phỏng. Nếu Aiven có IP allowlist, bổ sung outbound IP của đúng service Render trong Aiven trước khi thử lại. Không tắt TLS hoặc bỏ xác minh CA để chữa lỗi kết nối.
5. Mở UTEExpress local, tạo/chọn một đơn MoMo của tài khoản User, quét QR bằng điện thoại. Kiểm tra mã đơn/số tiền rồi xác nhận mô phỏng. Local phải tự hiện PAID và cùng mã giao dịch từ Aiven. Thử đổi sang User khác: không được xem QR/trạng thái đơn của chủ cũ. Thử nhấn hai lần không tạo hai mã giao dịch.

Render Free ngủ sau 15 phút không hoạt động và có thể mất khoảng một phút để khởi động lại. Chu kỳ đọc database 3 giây ở local không loại bỏ được độ trễ đánh thức của Render. Xem [giới hạn Free](https://render.com/docs/free), [Docker](https://render.com/docs/docker) và [Blueprint](https://render.com/docs/blueprint-spec). Blueprint đang tắt auto-deploy để tránh redeploy ngoài ý muốn.

Dockerfile dùng user không đặc quyền; giới hạn heap 256 MB, metaspace 160 MB. `-Pmomo-simulator` chỉ đóng gói tài nguyên MoMo, không đóng gói `application.properties`/cấu hình riêng hoặc template User/Admin. Docker daemon trên máy chưa chạy, vì vậy chưa xác minh image Docker bằng local daemon; gói JAR riêng đã được kiểm thử khởi động và đọc Aiven. Deploy thực tế trên Render vẫn là bước cần kiểm tra tiếp.

## Kiểm thử

Kết quả kiểm thử hồi quy sau khi bổ sung khởi động tự động: 846 ca Java (0 lỗi/thất bại) và 49 ca JavaScript đều đạt. Hai smoke test đọc Aiven/TLS và khởi động JAR riêng cũng đạt. Các kết quả này không thay thế bước kiểm thử trên service Render và quét QR bằng điện thoại thật sau khi deploy.

```powershell
mvn '-Dtest=*,!Aiven*IntegrationTest' test
node --test src/test/js/staff-navigation.test.cjs src/test/js/tracking-realtime.test.cjs src/test/js/assistant-chat.test.cjs src/test/js/assistant-widget.test.cjs src/test/js/momo-payment.test.cjs
mvn -Pmomo-simulator '-Dmaven.test.skip=true' clean package
mvn '-Dtest=AivenMomoConnectionIntegrationTest,AivenMomoPackagedSmokeIntegrationTest' test
```

Các test nghiệp vụ/concurrency dùng H2 in-memory tách biệt, không phải database ứng dụng. Hai test Aiven MoMo ở dòng cuối chỉ đọc kết nối/TLS/schema/health; không tạo đơn/tài khoản, không ghi trạng thái PAID. Không chạy bừa các Aiven integration test khác của project vì một số test cũ tạo dữ liệu QA.

Các nhóm kiểm tra: sửa từng byte vé mã hóa, vé sai khóa/hết hạn, origin QR sai, giải mã QR qua nhiều nonce, cô lập tài khoản/quyền, mật khẩu/khóa tài khoản, cước/giảm giá không khớp, miễn cước, thiếu biên nhận, khóa cạnh tranh với hủy đơn, xác nhận trùng, CSRF, phiên trình duyệt, hiển thị template thực, endpoint local bị gỡ, JAR không chứa cấu hình private và trạng thái poller khi offline/ẩn tab/timeout/quay lại từ bfcache.

## Commit riêng theo chức năng (chạy lần lượt, chưa tự commit/push)

Không dùng `git add .`: hiện có `application.properties` đã sửa riêng chứa cấu hình nhạy cảm và các thay đổi cũ không thuộc MoMo. `.gitignore` không bảo vệ một file vốn đã tracked. Không thêm README, `.project`, file backup bị xóa hoặc file `tash ...` ngoài ý muốn.

### Commit 1 — thanh toán local, QR an toàn, COD/MoMo và validation

```powershell
git add .gitignore config/momo-local.properties.example src/main/java/vn/edu/hcmute/uteexpress/config/MomoConfiguration.java src/main/java/vn/edu/hcmute/uteexpress/config/SecurityConfig.java src/main/java/vn/edu/hcmute/uteexpress/controller/user/PaymentController.java src/main/java/vn/edu/hcmute/uteexpress/controller/user/UserOrderController.java src/main/java/vn/edu/hcmute/uteexpress/dto/OrderCreateRequest.java src/main/java/vn/edu/hcmute/uteexpress/entity/OrderPayment.java src/main/java/vn/edu/hcmute/uteexpress/security/admin/PublicAccountSessionFilter.java src/main/java/vn/edu/hcmute/uteexpress/service/OrderPaymentService.java src/main/java/vn/edu/hcmute/uteexpress/service/impl/OrderDraftServiceImpl.java src/main/java/vn/edu/hcmute/uteexpress/service/impl/OrderPaymentServiceImpl.java src/main/java/vn/edu/hcmute/uteexpress/service/impl/MomoPaymentServiceImpl.java src/main/java/vn/edu/hcmute/uteexpress/service/payment src/main/java/vn/edu/hcmute/uteexpress/repository/payment src/main/resources/templates/user/payment-gateway.html src/main/resources/templates/user/order-form.html src/main/resources/static/js/momo-payment.js src/main/resources/assistant/user-guide.txt src/test/java/vn/edu/hcmute/uteexpress/service/impl/OrderPaymentGuardTest.java src/test/java/vn/edu/hcmute/uteexpress/tracking/CrossRoleSynchronizationIntegrationTest.java
git add src/main/java/vn/edu/hcmute/uteexpress/service/impl/MomoHealthClientImpl.java src/main/java/vn/edu/hcmute/uteexpress/service/impl/MomoStartupServiceImpl.java
git diff --cached --stat
git commit -m "feat(payment): QR MoMo an toan va dong bo Aiven, bo VNPay moi"
```

### Commit 2 — ứng dụng mô phỏng và giao diện riêng trên Render

```powershell
git add pom.xml .dockerignore render.yaml deploy/momo src/main/java/vn/edu/hcmute/momosimulator src/main/resources/momo-simulator.properties src/main/resources/templates/momo src/main/resources/static/css/momo-simulator.css src/main/resources/static/js/momo-simulator.js
git diff --cached --stat
git commit -m "feat(momo): web mo phong responsive rieng cho Render"
```

### Commit 3 — kiểm thử hồi quy, quyền truy cập và hướng dẫn vận hành

```powershell
git add src/test/java/vn/edu/hcmute/uteexpress/payment src/test/js/momo-payment.test.cjs src/test/js/momo-preview-server.cjs docs/thang/MOMO_RENDER_2026_10_19.md
git diff --cached --stat
git commit -m "test(payment): validation, dong thoi, TLS va polling MoMo"
git push origin feature_Thang
```

Không commit file `config/momo-local.properties`, mật khẩu Aiven hoặc CA/truststore. Khi source đã lên Git và Render đã kết nối, tiếp tục deploy/test QR thật; chỉ khi bước đó thành công mới coi luồng online hoàn tất.
