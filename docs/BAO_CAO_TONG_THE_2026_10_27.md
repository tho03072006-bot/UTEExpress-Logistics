# Rà soát tổng thể UTEExpress ngày 27/10/2026

Mốc kiểm thử: nhánh `feature_Tho_lien_tinh`, nền `46e641c` cùng các thay đổi được liệt kê trong báo cáo. Tài liệu ghi kết quả ngày 27/10, giờ Việt Nam, trước lượt commit/push được chủ repo yêu cầu sau đó; nối tiếp `MA_TRAN_KIEM_THU_SU_CO_2026_10_26.md`.

## 1. Kết quả đã xác minh

| Kiểm tra | Tổng | Failure | Error | Skipped |
|---|---:|---:|---:|---:|
| Full Maven sau mọi sửa đổi | **1.625 test / 129 suite** | **0** | **0** | **0** |
| JavaScript | **116** | **0** | — | **0** |
| Render trang, quyền truy cập và thanh toán | 88 | 0 | 0 | 0 |
| Hồi quy vòng đời khiếu nại | 15 | 0 | 0 | 0 |
| Lịch cước và khuyến mãi đồng thời | 4 | 0 | 0 | 0 |
| Tích hợp trợ lý | 81 | 0 | 0 | 0 |
| Hội thoại trợ lý | 19 | 0 | 0 | 0 |
| Giao tiếp với nhà cung cấp trợ lý | 16 | 0 | 0 | 0 |
| Giới hạn lượt dùng trợ lý | 2 | 0 | 0 | 0 |

Các hàng chi tiết là thành phần của full suite, không cộng thêm vào 1.625. So với bộ 1.426 test đầu lượt, bổ sung **199 ca**. Full cuối kết thúc **13:44:14 ngày 27/10/2026**, mất **4 phút 53 giây**. Đã đối chiếu tổng từ 129 XML được tạo sau mốc bắt đầu của chính lượt này, không cộng XML cũ.

Đã chạy package simulator trước full test. Tùy chọn `maven.test.skip=true` chỉ dùng cho bước đóng gói simulator; lượt `mvn test` sau đó chạy toàn bộ, không loại test Aiven, không thêm `@Disabled`, không thay cấu hình test để bỏ lỗi. Hai suite Aiven từng có lỗi (`AivenControlsIntegrationTest`, `AivenOperationsIntegrationTest`) hiện đều đạt 2/2.

Kết quả trên xác nhận bộ ca đã chạy. Không có cơ sở khẳng định đã thử hết mọi dữ liệu, thứ tự thao tác hoặc tình huống vật lý có thể phát sinh.

## 2. GitHub và bảo toàn thay đổi local

Đã `git fetch origin` và `git pull --ff-only` trên nhánh hiện tại; pull trả **Already up to date**.

| Nhánh remote | HEAD sau fetch | Ghi nhận |
|---|---|---|
| `feature_Tho_lien_tinh` | `46e641c` | Local và remote 0 ahead / 0 behind |
| `develop` | `e25b1a4` | Có 15 commit sau HEAD của nhánh hiện tại, gồm 7 commit mới so với mốc `07a9337` đã kiểm trước |
| `feature_Thang` | `d0cbf56` | Có 6 commit mới sau `66d524e` |
| `feature_Tai` | `62056d1` | Không đổi ở lần fetch này |
| `feature_Tho` | `9746ad7` | Không đổi ở lần fetch này |
| `main` | `beaadae` | Không đổi ở lần fetch này |

Nhóm thay đổi mới trên develop gồm OTP/mật khẩu đồng thời, tài khoản bị khóa khi request còn giữ dữ liệu cũ, lịch cước chồng nhau, đồng bộ chứng từ thanh toán và cảnh báo Manager. Đã đối chiếu và đưa có chọn lọc các bản sửa liên quan cùng ca hồi quy vào code đang làm, sau khi tái hiện lỗi trên nhánh này. Không gộp lịch sử develop, không chạy merge/rebase/cherry-pick.

Tại thời điểm kiểm thử, chưa commit, push, stage hoặc mở PR. 43 file đã thay đổi ở đầu lượt được sao lưu tại `target/review-baseline-20261027/`; đối chiếu SHA-256 của toàn bộ bản sao đều đúng, không thiếu file gốc. Không dùng stash/reset/checkout--/clean. Không sửa `Order.java`, `AppUser.java` hoặc enum trạng thái đơn.

Chỉ kiểm tra tồn tại: có 5 file `config/*-local.properties`, có file trong `config/certificates/`, có `src/main/resources/application-local.properties`. Không mở hoặc in nội dung các file này. Thư mục sao lưu tài khoản và file application-local vẫn được gitignore.

## 3. Lỗi xác nhận được và cách sửa

### 3.1. OTP và mật khẩu dùng dữ liệu tài khoản cũ

Khi advice/OSIV đã đọc tài khoản, truy vấn khóa dòng chưa bảo đảm entity trong bộ nhớ đã được làm mới. Request cũ có thể gặp lỗi khóa lạc quan thay vì từ chối rõ ràng khi OTP đã dùng hết, hoặc dùng thông tin mật khẩu/giới hạn gửi cũ.

Đã làm mới tài khoản sau khi khóa trong các luồng OTP đăng ký, khôi phục mật khẩu, đổi email và đổi mật khẩu. Kiểm tra cả giới hạn gửi chung, OTP đã sử dụng, năm lần nhập sai, mật khẩu vừa được đổi ở request khác và tác động thu hồi phiên/yêu cầu đổi email.

### 3.2. Form hồ sơ và sổ địa chỉ sau khi Admin khóa tài khoản

Các thao tác sửa hồ sơ, avatar, thông báo, tạo/sửa/xóa/đặt mặc định địa chỉ cần kiểm tra trạng thái tài khoản mới nhất, thay vì tin entity đã tải trước. Đã khóa và làm mới tài khoản trước khi ghi. Service hồ sơ cũng kiểm tra form null/validation, tránh tự cắt ngắn dữ liệu sai và lưu như dữ liệu hợp lệ.

23 ca mới trong `PublicValidationIntegrationTest` bao phủ các hồi quy ở 3.1–3.2, kể cả cập nhật hợp lệ đi qua các lời gọi service lồng nhau.

### 3.3. Hai lịch cước có thể chồng nhau khi tạo đồng thời

Khóa cước gốc nhưng dùng read view cũ vẫn có thể không thấy lịch mà request trước vừa commit. Đã dùng READ_COMMITTED cho lập lịch; khóa cùng dịch vụ rồi kiểm tra giao khoảng trên dữ liệu mới. Chặn thời gian có phần giây/thập phân vì form và dữ liệu lịch cước sử dụng độ chính xác theo phút.

Test dùng H2 SNAPSHOT để mô phỏng ảnh chụp toàn giao dịch: hai lịch chồng nhau chỉ một lịch thành công; hai lịch tiếp giáp hợp lệ cùng tồn tại, thời điểm kết thúc là biên loại trừ; chặn khoảng dưới một phút; hai request dùng lượt khuyến mãi cuối chỉ một lượt được ghi.

### 3.4. Khiếu nại: dữ liệu cũ, đổi shipper và nhận xử lý đồng thời

Đã tái hiện ba lỗi bằng giao dịch commit thật:

- Khách nhắn vào hồ sơ vừa được Manager giải quyết: có thể lỗi JPA thay vì mở lại hồ sơ mới nhất.
- Shipper cũ có order trong cache vẫn gửi được thông tin bổ sung sau khi đơn đã đổi shipper.
- Form có revision cũ hoặc hai Manager cùng nhận hồ sơ có thể phát sinh lỗi khóa lạc quan thay vì thông báo cập nhật lại form.

Đã thống nhất khóa **vận đơn trước, hồ sơ sau**, làm mới cả order và complaint trước khi kiểm quyền/revision. Các thao tác ghi khiếu nại dùng READ_COMMITTED để thấy kết quả vừa commit sau khi chờ khóa. Không dùng truy vấn khóa complaint trên entity còn giữ revision cũ trước bước làm mới. Thứ tự khóa khớp với hủy đơn và đề xuất bồi thường.

15 ca hồi quy kiểm đủ bốn trạng thái khiếu nại cho tin nhắn khách và bổ sung của shipper; mở lại hồ sơ; giữ người phụ trách/lịch sử; Admin tiếp quản; Manager khác không phản hồi thay; timeline trên 50 mục mở trang cuối; tạo khiếu nại trùng và hai Manager nhận xử lý đồng thời. Các test cũ về bồi thường, người lập/người duyệt, chứng từ, revision và chi nhánh vẫn chạy đầy đủ.

### 3.5. Số điện thoại có định dạng lọt vào ngữ cảnh trợ lý

Che số điện thoại trước đây chỉ nhận chuỗi số liền. Đã bổ sung định dạng có dấu cách, dấu chấm, gạch ngang, mã `+84` và `+84(0)` trước khi gửi câu hỏi sang nhà cung cấp. Sáu ca kiểm trực tiếp nội dung truyền tới gateway; một ca giữ nguyên mã vận đơn và số tiền hợp lệ. Không thay dữ liệu người dùng lưu trong hệ thống.

### 3.6. Trang thanh toán chỉ tin nhãn PAID

Ba ca trên template thật đã tái hiện thông báo “Đã thanh toán” sai khi thiếu biên nhận MoMo, thiếu thời điểm thu tiền hoặc số tiền không khớp cước đã chốt.

Đã dùng `PaymentCollectionView` kiểm cùng dữ liệu nghiệp vụ: cước, giảm giá, số tiền, phương thức, trạng thái và chứng từ. Trường hợp chưa hợp lệ hiện cần tra soát; chỉ tạo QR khi thực sự chờ trả ví. COD được miễn cước và ví có tổng 0 nhưng chưa xác nhận được phân biệt. `PaymentReadiness` cũng chặn bản ghi PAID thiếu `paid_at`. Có 8 test mô hình hiển thị và 3 ca HTTP hồi quy cho lỗi này.

### 3.7. Giao diện và ký tự

Nhãn trạng thái khiếu nại dùng xanh/xám; tin bổ sung của shipper dùng nền xám; thông báo thanh toán vừa sửa dùng khung trung tính. Giữ chữ tiếng Việt và nội dung trạng thái để phân biệt ý nghĩa, không phụ thuộc màu.

Kiểm tra UTF-8 nghiêm ngặt và dấu hiệu lỗi giải mã trên **674 file Java/HTML/JS/CSS/TXT**: không có file lỗi. Test render kiểm `lang="vi"`, charset UTF-8, tiêu đề, chuỗi tiếng Việt đầy đủ dấu và escape nội dung `<script>` trong hội thoại. Đây không phải kiểm tra vị trí pixel, ảnh, camera hoặc mọi kích thước màn hình.

## 4. Ma trận kiểm thử các vai trò và tương tác

| Phạm vi | Ca tự động đã chạy |
|---|---|
| Guest | Render trang chủ, tra cứu, ước tính, đăng nhập/đăng ký/quên mật khẩu; validation dữ liệu; giá theo tuyến; bảo vệ endpoint yêu cầu đăng nhập |
| User | Tạo đơn, idempotency khi gửi lặp/đồng thời, giỏ nháp và xác nhận, sổ địa chỉ, cập nhật hồ sơ/OTP, hủy trước lấy, thanh toán ví mô phỏng/COD, xem hành trình/bằng chứng, đánh giá và khiếu nại |
| Shipper | Đơn đúng phân công, tất cả 49 cặp chuyển trạng thái, sáu lý do lấy/giao thất bại, hẹn/giao lại, bằng chứng/chữ ký, lịch sử, QR, lộ trình, thông báo, thống kê và đối soát |
| Manager | Phạm vi chi nhánh, phân công/gợi ý/sức chứa, nhận tại quầy, kho/chuyến xe/niêm phong/thiếu kiện/nhận bổ sung, cảnh báo, khiếu nại, hồ sơ hoàn/giao lại/tiền, COD, khuyến mãi và báo cáo |
| Admin | Quyền Manager, quản lý tài khoản và khóa phiên, cấu hình chi nhánh/cước/chính sách, lịch cước đồng thời, duyệt/hoàn tất hồ sơ tiền đúng vai trò, audit và truy cập API |
| Liên vai trò | Người gửi tạo/hủy ↔ shipper nhận/lấy; Manager phân công ↔ shipper cập nhật ↔ người gửi thấy tiến trình; DROP_OFF ↔ kho; kho gửi ↔ chuyến ↔ kho nhận ↔ shipper giao; giao thất bại ↔ giao lại/hoàn; khiếu nại ↔ đề xuất/duyệt/biên nhận bồi thường; COD ↔ Manager xác nhận ↔ Admin xử lý khoản lệch |
| Đồng thời | Phân công và giao lại tranh suất cuối, hai lượt giao lại, hai lần lấy/thất bại, hủy lúc lấy/nhận quầy, hai yêu cầu hoàn/giao lại, bấm hoàn tất tiền hai lần, thanh toán/hủy, đối soát, dùng lượt khuyến mãi cuối, trùng token/form, OTP và lịch cước |

Bộ render mới gồm **75 trang hợp lệ** (Guest 7, User 12, Shipper 12, Manager 17, Admin 27), **10 ca chặn sai vai trò** và **3 ca thanh toán không nhất quán**. Dùng principal được xác thực và template/service thật trên H2. Các kiểm tra này không đăng nhập vào tài khoản thật hoặc ghi đơn lên Aiven.

Các suite vận hành tiêu biểu trong full: `ShipmentIncidentMatrixIntegrationTest`, `DispatchRecoveryIntegrationTest`, `DispatchRulesIntegrationTest`, `DropOffIntegrationTest`, `HubConcurrencyIntegrationTest`, `InterprovincialReturnIntegrationTest`, `CrossRoleSynchronizationIntegrationTest`, `OperationsControlsIntegrationTest`, các suite payment/COD/account/security/validation. Toàn bộ kết quả từng suite nằm trong CSV bằng chứng ở mục 8.

## 5. Trợ lý: phạm vi và bằng chứng

- Trợ lý hiện phục vụ **User và Shipper**; Guest/Manager/Admin không được gọi API trợ lý bằng vai trò của mình. Đây là phạm vi hiện có, không phải thiếu kiểm tra vai trò.
- Đã kiểm tra tra cứu đúng chủ/đúng phân công, đơn không tồn tại và đơn không được phép không phân biệt được, chọn đơn/phân trang, đổi tài khoản/đăng xuất/khóa phiên trong lúc chờ, thay đổi trạng thái hoặc đổi shipper khi đang sinh trả lời, CSRF, JSON sai/nhúng định danh, injection, giới hạn tool/vòng lặp/lượt gọi, retry và chống gửi trùng.
- Thêm **54 tổ hợp**: 9 cặp chi nhánh × 3 dịch vụ × 2 cách lấy hàng. 42 tổ hợp hợp lệ khớp cước và thời gian tuyến với hàm báo giá của khách; 12 tổ hợp Hỏa tốc liên tỉnh bị từ chối ở cả hai nơi.
- Các lỗi upstream 401/403/429/quota/5xx/timeout, phản hồi không hợp lệ/quá lớn, thiếu cấu hình được kiểm bằng gateway giả. Những test này không dùng mạng thật.
- Đã chạy `CheckGroqConnection` với dữ liệu health giả: xác thực API, một lần gọi tool và câu trả lời cuối thành công, exit 0. Không in khóa, không gửi dữ liệu vận đơn/người dùng thật.

Không suy ra mọi câu trả lời tự nhiên của mô hình đều chính xác từ một health check. Hạn chế bịa thông tin và quyền đọc dữ liệu được kiểm bằng các ca xác định; vẫn cần đối chiếu nghiệp vụ khi nghiệm thu câu trả lời thực tế trên giao diện.

## 6. Aiven và app đang chạy

| Công cụ | Kết quả --check | Exit |
|---|---|---:|
| `UpgradeAivenBranches` | 0/11 bảng thiếu; max_active_orders đã có; thiếu 0 chi nhánh/khu vực/lân cận/biểu giá | 0 |
| `UpgradeAivenDropOff` | pickup_mode và drop_off_discount đầy đủ | 0 |
| `UpgradeAivenReturnLeg` | return_leg đầy đủ | 0 |
| `UpgradeAivenLegacyRoutes` | Có thể thêm 0 tuyến; 39 đơn cần xử lý tay theo quy tắc chuyển tiếp | 0 |

Không cần migration mới cho các sửa đổi trong lượt này. Không chạy `--apply`, không thay đổi schema hoặc dữ liệu nghiệp vụ lâu dài trên Aiven. Các test tích hợp Aiven được phép vẫn chạy với rollback.

App `mvn spring-boot:run` khởi động lại thành công ở **http://localhost:8080**, log `target/review-20261027-app-final.log`, nạp code mới. Web thử riêng ở **http://localhost:8085** dùng H2 in-memory, fake cloud, tắt email cập nhật đơn; log `target/review-20261027-web-final.log`. Chỉ tiến trình do lượt này tạo được dừng/khởi động lại, đã kiểm tra nhận dạng trước khi dừng.

## 7. Chưa thể ghi nhận hoàn tất và đề xuất tiếp theo

### 7.1. UI trực tiếp còn bị cổng quyền chặn

Truy cập `http://localhost:8085` bị thiết lập quyền đã lưu của trình duyệt từ chối. Chủ repo đã ủy quyền lại; lần thử sau vẫn bị từ chối ở cổng quyền. Không đổi origin/browser, không dùng HTTP/CDP hoặc kênh khác để thực hiện thay thao tác web bị chặn. Không có ảnh nghiệm thu giao diện mới ngày 27/10.

Vì vậy chưa ghi đạt cho kiểm tra bố cục trực tiếp trên desktop/điện thoại, thao tác file/camera và trải nghiệm trợ lý thật trong widget. Các ca render/JavaScript/service đã đạt được ghi riêng, không gọi chúng là nghiệm thu trình duyệt.

Hồ sơ và mã vận đơn hoàn hàng đã nghiệm thu ngày 26/10 vẫn được ghi trong báo cáo ngày đó. Dữ liệu web H2 không tồn tại sau khi dừng tiến trình; không dùng mã cũ để tuyên bố đã nghiệm thu lại ngày 27/10. Nghiệp vụ hoàn liên tỉnh trong full test mới vẫn đạt.

### 7.2. Thiếu luồng nghiệp vụ riêng cho xác nhận mất hàng

Có luồng thiếu kiện, nhận bổ sung, khiếu nại và bồi thường. Chưa có phân loại riêng “đã xác nhận mất” để khóa giao/giao lại/COD, kết thúc trách nhiệm vận đơn và giải phóng tải. Hoàn tất hồ sơ bồi thường hiện không tự đóng vận đơn.

Không tự coi mọi hồ sơ bồi thường là mất hàng: bồi thường còn áp dụng cho hư hỏng hoặc hoàn cước. Cần chốt cách phân loại và trạng thái nghiệp vụ trước khi thêm metadata/migration; giữ nguyên ràng buộc không sửa OrderStatus. Đây là khoảng trống chức năng, không được ghi thành luồng đã đạt chỉ vì các test bồi thường xanh.

### 7.3. Giới hạn khác

39 đơn cũ cần xử lý tuyến thủ công vẫn chưa thay đổi. Bước shipper kho gửi tự đi trả kiện hoàn là tùy chọn trong bàn giao; hiện Manager kho gửi xác nhận bàn giao có biên nhận. Chưa kiểm camera/ảnh hàng vật lý, email đến hộp thư thật, tiền thật, tải lớn hoặc mọi hoán vị thao tác/địa bàn. Các fixture và gateway giả không chứng minh những tích hợp này đã đạt ngoài thực tế.

### 7.4. Đề xuất gộp develop để nhóm quyết

Ở mốc hiện tại HEAD chưa có commit mới; develop đi trước 15 commit. Các thay đổi local chưa nằm trong phép đếm ahead/behind. Không tự cập nhật develop hoặc đổi nhánh trong lượt này.

Sau khi chủ repo duyệt code và cho phép commit/gộp, nhóm có thể chọn:

1. Tạo nhánh tích hợp từ develop mới nhất, đưa sang các phần logistics/khiếu nại/trợ lý chưa có trên develop; loại phần sửa OTP/tài khoản/cước/thanh toán đã giống develop. Ưu điểm là nền tích hợp rõ; rủi ro bỏ sót thay đổi local, cần đối chiếu manifest và full test.
2. Commit các phần đã duyệt trên feature hiện tại, sau đó gộp develop bằng merge thường khi được cho phép. Giữ được lịch sử phát triển; rủi ro xung đột ở điều phối/cảnh báo/thanh toán và dữ liệu hiển thị. Cần rà lại bằng chứng giao hàng, timeline, chi nhánh và chạy full suite sau xử lý xung đột.

Chưa thực hiện phương án nào; không mở PR, không force push hoặc sửa lịch sử.

## 8. Bằng chứng và lệnh tái lập

```powershell
mvn -B -Pmomo-simulator '-Dmaven.test.skip=true' package
mvn -B test
node --test src/test/js/*.test.cjs
```

Tất cả đường dẫn dưới đây nằm trong `D:\WEB\Project_CK\target\`, không commit log hoặc dữ liệu thử:

- `review-20261027-complete-test.log`, `review-20261027-complete-test-summary.csv`, `review-20261027-complete-summary.json`: lượt full cuối 1.625 test.
- `review-20261027-final-package.log`, `review-20261027-complete-js.log`: đóng gói và JavaScript.
- `review-20261027-baseline-test.log`: bộ cũ 1.426 đạt trước bổ sung ca hồi quy.
- `review-20261027-regression-before.log`: 118 ca, 30 failure/3 error trước sửa; `review-20261027-regression-after.log`: 201 ca liên quan đạt sau sửa đầu tiên.
- `review-20261027-final-test.log`: lượt trung gian 1.529 ca có 1 error mới ở hai Manager nhận cùng hồ sơ; lỗi này đã được sửa trước full cuối.
- `review-20261027-complaint-concurrency.log`: 160 ca vận hành/khiếu nại đạt sau sửa đồng thời.
- `review-20261027-payment-before.log`: 3 ca thanh toán đều tái hiện lỗi trước sửa; `review-20261027-final-focused.log`: 137 ca liên quan đạt sau sửa.
- `review-20261027-UpgradeAiven*-check.log`: bốn lần kiểm schema/danh mục chỉ đọc.
- `review-20261027-utf8.json`: kiểm tra ký tự; `review-baseline-20261027/manifest.json`: SHA-256 bản sao đầu lượt.

Sau kiểm thử, chủ repo đã yêu cầu commit và push bản cập nhật lên `feature_Tho_lien_tinh`. Xem nhật ký đồng bộ từ mốc kiểm thử bằng `git log --oneline 46e641c..HEAD`. Không gộp develop hoặc mở PR trong lượt đồng bộ này; không đưa log, dữ liệu H2 hay file bí mật vào commit.
