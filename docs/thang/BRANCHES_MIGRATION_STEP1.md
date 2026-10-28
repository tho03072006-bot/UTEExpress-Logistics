# Bước 1 — schema chi nhánh và khu vực giao nhận

Thực hiện trên `develop`, chỉ thay đổi local, chưa commit/push. Không tự chạy `--apply`.

## Phạm vi

- 11 bảng mới: `branch`, `province_area`, `area_neighbor`, `staff_branch`, `shipper_zone`, `order_route`, `linehaul_trip`, `linehaul_item`, `route_tariff`, `saved_address_area`, `draft_order_area`.
- `operations_policy.max_active_orders INT NOT NULL DEFAULT 15`; không cập nhật các giá trị chính sách đang có.
- Entity ở `src/main/java/vn/edu/hcmute/uteexpress/entity/routing/`, repository ở `repository/routing/`. Đăng ký các entity mới trong `tool/database/AivenSchema.java`.
- `Order.java`, `AppUser.java`, enum dùng chung và phần ảnh/chữ ký giao hàng được giữ nguyên.
- Thêm `/config/demo-account-backups/` vào `.gitignore`; không đọc nội dung backup.

## Danh mục

`src/main/resources/db/branches/areas.csv`: 59 khu vực giao nhận nội bộ đặt theo địa danh cũ: SGN 22, HAN 30, DAD 7; không nhận Hoàng Sa. Đây là phạm vi phục vụ cố định của đồ án, không phải danh mục hành chính hiện hành hoặc toàn bộ địa bàn của các thành phố sau thay đổi địa giới.

Mã tỉnh được dùng làm mã danh mục: SGN `79`, HAN `01`, DAD `48`. Mã khu vực có tiền tố chi nhánh, ví dụ `SGN-THUDUC`, `HAN-CAUGIAY`, `DAD-HAICHAU`.

Tọa độ tâm **xấp xỉ, dùng cho sắp lộ trình** và tính khoảng cách của đồ án; không phải tọa độ từng địa chỉ hoặc khoảng cách đường bộ. Không dùng địa chỉ nhà shipper, GPS hoặc API bản đồ trả phí.

`neighbors.csv` có 135 cặp lân cận lập tay theo ranh giới cũ; `BranchSeedData` nạp thành **270 quan hệ có hướng**, luôn có chiều ngược, không tự nối mình và không nối khác chi nhánh. Danh mục phục vụ xếp hạng gợi ý; không đại diện dữ liệu địa giới/GIS chính thức.

Kho dùng tâm khu vực: SGN `SGN-THUDUC`, HAN `HAN-CAUGIAY`, DAD `DAD-HAICHAU`. `hub_address` ban đầu là nhãn kho theo khu vực, không tự đặt số nhà/đường. `hub_area_code` lưu mã để tránh vòng khóa ngoại giữa chi nhánh và khu vực khi tạo chi nhánh; service quản lý chi nhánh ở bước 2 phải kiểm tra kho thuộc đúng chi nhánh.

21 biểu giá có hướng: 9 biểu giá nội tỉnh (3 dịch vụ × 3 chi nhánh), 12 biểu giá liên tỉnh (2 dịch vụ × 6 chiều). Hỏa tốc liên tỉnh không có biểu giá và bị ràng buộc CHECK từ chối. Phụ phí/thời gian mặc định theo B7; giá đã cấu hình không bị ghi đè khi chạy lại.

`draft_order_area` có một dòng duy nhất cho mỗi đơn nháp, lưu riêng hai cặp tỉnh/khu vực gửi–nhận. Các bảng tuyến, nhân sự, chuyến và địa chỉ phụ chưa được gán/điền dữ liệu nghiệp vụ trong bước này. Công cụ không tự tạo tài khoản demo hoặc chuyển dữ liệu đơn cũ; đó là bước 16.

## Cơ chế migration

- `UpgradeAivenBranches --print-schema`: xuất DDL và toàn bộ SQL nạp danh mục, không đọc cấu hình kết nối và không mở kết nối.
- `--check`: kết nối chỉ đọc; kiểm tra MySQL 8+, các bảng nền, cột/kiểu/độ dài/nullability, độ chính xác số, khóa chính, unique, tên/cột/bảng đích và hành vi ON DELETE/ON UPDATE của từng khóa ngoại, DEFAULT 15 và danh mục còn thiếu. Khóa khác định nghĩa mong muốn được báo rõ tên khóa, định nghĩa mong muốn và thực tế; không tự sửa khóa có sẵn. Không lấy khóa migration, không chạy DDL/DML.
- Schema có sẵn không tương thích: dừng, không tự sửa kiểu, xóa hoặc ghi đè.
- `--apply`: chỉ người vận hành tự chạy. Lấy khóa `uteexpress-schema-initialize`, kiểm tra lại, tạo bảng còn thiếu và thêm cột nếu chưa có. DDL MySQL tự commit; khi lỗi có thể còn cấu trúc đã tạo, không tuyên bố rollback được DDL.
- INSERT danh mục chạy trong giao dịch riêng, dùng `WHERE NOT EXISTS`; không UPDATE/DELETE, không ghi bảng tài khoản, vận đơn hoặc phiếu nghiệp vụ. Chạy lần lượt từng `executeUpdate`, mỗi câu có timeout 30 giây riêng; không dùng `executeBatch` với timeout chung cho cả 353 lượt mạng. Lỗi seed sẽ rollback giao dịch INSERT; chạy lại không ghi đè cấu hình đang có. Nếu đã đầy đủ, công cụ không ghi lại.
- Kiểm tra `DATETIME(6)` theo JDBC `TIMESTAMP`/MySQL `TYPE_NAME=DATETIME` và `COLUMN_SIZE=26` (19 ký tự ngày giờ + dấu chấm + 6 chữ số phần giây), không dùng `DECIMAL_DIGITS` vì Connector/J 8.3 trả 0. `DECIMAL` vẫn kiểm tra precision/scale; `BIT` phải rộng 1 bit; native `ENUM` chấp nhận JDBC `CHAR` hoặc `VARCHAR` nhưng bắt buộc `TYPE_NAME=ENUM`.
- Khi lỗi, in tên lớp exception, SQLState (kể cả null), mã lỗi và thông báo SQLException. Thông báo lỗi xác thực/kết nối hoặc có dấu hiệu URL/user/host/password được che; không in stack trace/cấu hình. Lỗi rollback hoặc giải phóng khóa được giữ như lỗi bổ sung, không che mất lỗi seed gốc. Nếu rollback thất bại, không tự bật lại autocommit.
- Kết quả `--check`: exit `0` khi đầy đủ, `2` khi còn thiếu, `1` khi không tương thích hoặc lỗi kết nối. Exit `2` trước lần apply đầu là kết quả dự kiến.
- Công cụ không được gọi tự động khi ứng dụng khởi động.

### Chính sách xóa bản ghi cha

Chỉ bốn khóa ngoại sau dùng **ON DELETE CASCADE**, để code hiện có trên các máy vẫn xóa được địa chỉ, đơn nháp hoặc tài khoản chưa có lịch sử:

| Khóa ngoại | Bản ghi phụ được xóa theo bản ghi cha |
|---|---|
| `fk_saved_area_address` | `saved_address_area.saved_address_id` → `saved_address.id` |
| `fk_draft_area_order` | `draft_order_area.draft_order_id` → `draft_order.id` |
| `fk_staff_branch_user` | `staff_branch.user_id` → `app_user.id` |
| `fk_zone_shipper` | `shipper_zone.shipper_id` → `app_user.id` |

24 khóa ngoại còn lại giữ **ON DELETE RESTRICT**, bao gồm khóa tham chiếu vận đơn/chuyến và các cột người thao tác `assigned_by`, `departed_by`, `arrived_by`, `pickup_shipper_id`, `delivery_shipper_id`. Tất cả 28 khóa dùng **ON UPDATE RESTRICT**. Trong MySQL, NO ACTION có cùng hành vi chặn như RESTRICT nên `--check` chấp nhận hai giá trị này cho các khóa yêu cầu RESTRICT.

Mapping Hibernate của bốn quan hệ phụ cũng khai báo `@OnDelete(CASCADE)` để DDL từ `AivenSchema` nhất quán với công cụ migration. Không thêm `CascadeType.REMOVE` từ bản ghi phụ lên bản ghi cha. Xóa `shipment_order` đang có `order_route` vẫn bị từ chối.

## Kiểm tra trước apply

Chạy tại `D:\WEB\Project_CK`, JDK 21. Lệnh build không có tùy chọn bỏ qua test; nhóm test được chọn ở đây chỉ gồm unit/JDBC/Hibernate độc lập, không khởi động Spring context.

```powershell
Set-Location D:\WEB\Project_CK
mvn -B "-Dtest=BranchSeedDataTest,BranchSchemaSqlTest,UpgradeAivenBranchesTest,AivenDatabaseToolsTest#allTeamMappingsSupportMySqlWithoutConnectingToDatabase" test
mvn -B compile dependency:build-classpath "-Dmdep.outputFile=target/branches-classpath.txt"
$branchClasspath = 'target/classes;' + (Get-Content -LiteralPath target/branches-classpath.txt -Raw).Trim()
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $branchClasspath vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenBranches --print-schema
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $branchClasspath vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenBranches --check
```

Test mới kiểm tra danh mục/đối xứng/biểu giá; SQL trên H2 riêng và Hibernate validate toàn bộ mapping với schema mới mà không có Spring; tính chạy lại, giữ dữ liệu/cước cũ, các khóa/chặn dữ liệu sai; đường chỉ đọc và các tình huống thiếu bảng nền, sai cột/unique/FK/default, không lấy được khóa, lỗi DDL hoặc rollback seed. JDBC giả lập được dùng để thử đường ghi của công cụ; không apply lên Aiven.

### Kết quả đã chạy ngày 23/10/2026

- Sau chỉnh sửa tương thích metadata MySQL và seed từng câu, nhóm test độc lập nêu trên chạy xong lúc 15:09:55 (giờ Việt Nam): **42 tests, 0 failures, 0 errors, 0 skipped**. Trong đó `BranchSeedDataTest` 5, `BranchSchemaSqlTest` 10, `UpgradeAivenBranchesTest` 26 và một phương thức kiểm tra mapping của `AivenDatabaseToolsTest`. Đây chưa phải kết quả toàn bộ bộ test dự án.
- Test SQL thực sự xóa `saved_address`/`draft_order` có dòng area đi kèm, xóa nhân sự chỉ có cấu hình phụ; xác nhận bản ghi khác và danh mục khu vực vẫn còn. Các trường hợp xóa vận đơn đang có tuyến, người thao tác, shipper có lịch sử và chuyến có kiện đều bị chặn. Test JDBC xác nhận chạy migration lần hai không ghi lại, và bỏ qua ALTER khi `max_active_orders` đã tồn tại ngay từ đầu.
- Test tái hiện MySQL có `DATETIME(6)` với `DECIMAL_DIGITS=0`, `COLUMN_SIZE=26`, enum JDBC CHAR; schema đầy đủ nhưng rỗng vẫn nạp được 353 INSERT trong một giao dịch, không chạy lại CREATE/ALTER. Kiểm tra sai precision/scale/BIT/enum vẫn bị từ chối; chẩn đoán lỗi giữ được message khi SQLState=null và che chi tiết kết nối.
- `--print-schema`: xuất lại `BRANCHES_SCHEMA.sql` gồm 11 lệnh CREATE TABLE, một ALTER thêm cột chính sách và 353 INSERT danh mục; đúng **4 khóa ON DELETE CASCADE, 24 khóa ON DELETE RESTRICT**, tất cả ON UPDATE RESTRICT. Đã kiểm tra UTF-8 và không có ký tự thay thế U+FFFD. Khi chuyển hướng stdout của Java trên Windows cần các tùy chọn encoding ở trên để giữ nguyên tiếng Việt.
- Người vận hành đã chạy apply lần đầu trên MySQL 8.4.11 / Connector/J 8.3.0: 11 bảng và cột chính sách đã tạo, phần seed rollback. Sau bản sửa metadata/timeout, `--check` thực tế trên Aiven trả exit **2**: thiếu **0/11 bảng**, `max_active_orders INT NOT NULL DEFAULT 15` **đã có**, chỉ còn thiếu **3 chi nhánh, 59 khu vực, 270 quan hệ lân cận có hướng và 21 biểu giá**. Tất cả cột và khóa ngoại hiện có đã qua kiểm tra; lệnh chỉ đọc, không lấy khóa migration hoặc ghi dữ liệu.
- Trong lượt sửa này chỉ chạy unit test và `--check`; không chạy `--apply`, không sửa/xóa các bảng Aiven đã tạo. Chờ người vận hành apply lại để nạp danh mục. Chưa chạy toàn bộ `mvn test` hoặc khởi động ứng dụng.

## Lệnh người vận hành tự chạy sau khi review

Thông báo cả nhóm dừng app và review `BRANCHES_SCHEMA.sql` cùng kết quả `--check` trước khi tự chạy:

```powershell
Set-Location D:\WEB\Project_CK
$branchClasspath = 'target/classes;' + (Get-Content -LiteralPath target/branches-classpath.txt -Raw).Trim()
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $branchClasspath vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenBranches --apply
java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp $branchClasspath vn.edu.hcmute.uteexpress.tool.database.UpgradeAivenBranches --check
```

Đoạn SQL xuất ra phục vụ review. Dùng công cụ Java để apply vì nó kiểm tra schema/khóa và chỉ ALTER cột còn thiếu; không chạy nguyên tệp SQL một cách mù quáng.

## Sau khi người vận hành báo đã apply

Lúc đó mới chạy toàn bộ test, không loại test Aiven, không `@Disabled`, không thay cấu hình test:

```powershell
mvn -B test
```

Chỉ chạy giai đoạn này sau khi schema đã được apply. Nếu test smoke báo thiếu JAR simulator, xử lý việc chuẩn bị JAR rồi chạy lại đầy đủ; không thêm tùy chọn skip test. Báo tổng/failure/error/skipped thực tế, đối chiếu hai lỗi nền đã biết ở `AivenOperationsIntegrationTest` và `AivenControlsIntegrationTest`.

Sau toàn bộ test mới khởi động app ở cổng 8080 để kiểm tra giao diện cũ; bước 1 chưa thêm màn hình mới nên chưa cần mật khẩu Manager/Admin. Không bật `ddl-auto=update/create` để vượt qua lỗi thiếu schema.

## Git

Không commit/push trong bước này. Khi được yêu cầu “commit”, kiểm tra lại nhánh `feature_Tho`, đưa 2–3 phương án xử lý lịch sử và chờ lựa chọn trước khi thao tác. Chỉ add rõ từng file liên quan; không dùng `git add .` hoặc `git add -A`.
