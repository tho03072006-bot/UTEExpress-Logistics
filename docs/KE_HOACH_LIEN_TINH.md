# Kế hoạch mở rộng UTEExpress thành mạng lưới liên tỉnh 3 chi nhánh

Ngày lập: 23/10/2026 · Trạng thái: **bản đề xuất, cần cả nhóm chốt trước khi code**

## 1. Vấn đề hiện tại

- Hệ thống chưa có khái niệm chi nhánh, kho hay khu vực. Địa chỉ gửi và nhận là chuỗi tự do.
- Manager phân công **bất kỳ** shipper nào cho **bất kỳ** đơn nào. Shipper ở TP.HCM vẫn có thể bị giao một đơn ở Hà Nội.
- Dữ liệu mẫu đã có đơn đi Hà Nội, Đà Nẵng, Huế, Nha Trang, Cần Thơ, nhưng một shipper vẫn đi trọn từ lấy hàng tới giao.
- Trang Ước tính cước có phụ phí khoảng cách, còn lúc tạo đơn thì không tính. Nếu áp bậc phụ phí hiện có cho tuyến TP.HCM–Hà Nội (khoảng 1.700 km) thì riêng phụ phí đã khoảng 246.000 đ, quá xa thực tế.

## 2. Quy mô đã chốt

| Mục | Quyết định |
|---|---|
| Chi nhánh | 3 chi nhánh, mỗi chi nhánh có 1 kho (hub): **SGN** TP.HCM, **HAN** Hà Nội, **DAD** Đà Nẵng |
| Vùng phục vụ | Chỉ nhận lấy và giao trong 3 tỉnh/thành này. Địa chỉ ngoài vùng bị từ chối khi tạo đơn, báo "Chưa phục vụ khu vực này" |
| Loại tuyến | **Nội tỉnh**: lấy và giao cùng chi nhánh. **Liên tỉnh**: lấy ở chi nhánh gửi, trung chuyển sang chi nhánh nhận, giao ở chi nhánh nhận |
| Shipper | Mỗi shipper thuộc **đúng 1 chi nhánh**, chỉ chạy trong tỉnh của chi nhánh đó |
| Manager | Mỗi Manager thuộc 1 chi nhánh, chỉ điều phối đơn và shipper của chi nhánh mình |
| Admin | Toàn hệ thống: quản lý chi nhánh, gán nhân sự vào chi nhánh, cấu hình cước theo tuyến |
| Trung chuyển giữa kho | Không có tài khoản tài xế xe tải. Manager kho đi xác nhận "xuất kho"; Manager kho đến xác nhận "nhận kho" |

### 2.1 Cấp tài khoản nhân sự

- **Không có trang tự đăng ký cho shipper hay Manager.** Đăng ký công khai (có OTP email) chỉ dành cho người gửi (USER).
- Shipper ứng tuyển và ký hợp đồng **trực tiếp với chi nhánh** (giấy tờ, xe, bằng lái được kiểm tra ngoài hệ thống). Sau đó **Admin** tạo tài khoản ở trang Quản lý tài khoản, chọn **chi nhánh bắt buộc**. Shipper thuộc chi nhánh nào thì chỉ nhận việc ở chi nhánh đó.
- Đổi chi nhánh cho shipper: Admin sửa, và chỉ được đổi khi shipper không còn giữ đơn nào đang chạy.
- Số tài khoản tối thiểu để demo: **1 Admin, mỗi chi nhánh 1 Manager (tổng 3), mỗi chi nhánh 1–2 shipper**.

## 3. Vòng đời đơn

### Nội tỉnh (giữ gần như hiện tại)

```
Chờ lấy hàng → Đã lấy hàng → Đang giao → Giao thành công / Giao thất bại
   (một shipper của chi nhánh đó làm trọn)
```

### Liên tỉnh (mới)

```
[Chi nhánh gửi]                                    [Chi nhánh nhận]
Chờ lấy hàng → Đã lấy hàng → Nhập kho gửi → Đang trung chuyển → Nhập kho nhận → Đang giao → Giao thành công
   shipper lấy hàng         Manager gửi    Manager gửi           Manager nhận    shipper giao hàng
```

### Cách lưu trạng thái: giữ enum cũ, thêm giai đoạn tuyến

`Order.OrderStatus` đang được dùng ở rất nhiều nơi (chữ hiển thị, hành trình, biểu đồ, trợ lý, báo cáo). `Order.java` lại là entity dùng chung. Vì vậy **không thêm giá trị mới vào enum**, mà thêm bảng riêng `order_route` có cột `stage`:

| `OrderStatus` (giữ nguyên) | `order_route.stage` (mới) | Ý nghĩa |
|---|---|---|
| PENDING_PICKUP | PICKUP | Chờ shipper chi nhánh gửi lấy hàng |
| PICKED_UP | ORIGIN_HUB | Đã nhập kho chi nhánh gửi |
| PICKED_UP | LINEHAUL | Đang trung chuyển giữa hai kho |
| PICKED_UP | DEST_HUB | Đã nhập kho chi nhánh nhận, chờ phân công giao |
| IN_TRANSIT | LAST_MILE | Shipper chi nhánh nhận đang giao |
| DELIVERED / FAILED / RETURNED | (giữ giai đoạn cuối) | Kết thúc như hiện tại |

Lợi ích: code cũ vẫn chạy đúng với đơn nội tỉnh. Màn hình nào cần biết đơn đang ở kho nào thì đọc thêm `order_route`.

## 4. Mô hình dữ liệu mới

Tất cả là **bảng mới**, không thêm cột vào `shipment_order` hay `app_user`. Như vậy hai entity dùng chung không bị sửa.

| Bảng | Cột chính | Ghi chú |
|---|---|---|
| `branch` | id, code (SGN/HAN/DAD), name, province_code, hub_address, active, revision | Admin quản lý |
| `province_area` | province_code, district_code, district_name, branch_id, lat, lng | Danh mục quận/huyện của 3 tỉnh, kèm tọa độ trung tâm quận để sắp lộ trình (mục 5.4). Nạp sẵn bằng migration, không cho sửa trên web |
| `district_neighbor` | district_code, neighbor_code | Quận lân cận, dùng khi không có shipper phụ trách đúng quận |
| `shipper_zone` | shipper_id, district_code, primary | Khu vực phụ trách của shipper (mục 5.5) |
| `staff_branch` | user_id (unique), branch_id, assigned_by, assigned_at | Gán shipper và manager vào chi nhánh |
| `order_route` | order_id (unique), origin_branch_id, dest_branch_id, pickup_district, delivery_district, stage, pickup_shipper_id, delivery_shipper_id, revision | Một dòng cho mỗi đơn |
| `linehaul_trip` | id, code, from_branch_id, to_branch_id, status (LOADING/DEPARTED/ARRIVED), vehicle_plate, driver_name, driver_phone, seal_code, item_count, departed_by/at, arrived_by/at, arrival_note | Chuyến trung chuyển giữa hai kho. Tài xế xe tải là **thông tin của chuyến**, không phải tài khoản đăng nhập (xem mục 4.1) |
| `linehaul_item` | trip_id, order_id, received (true/false) | Đơn nằm trong chuyến nào. Kho nhận quét từng kiện để đánh dấu đã nhận |

### 4.1 Tài xế xe tải có cần role riêng không?

**Không tạo role mới trong đợt này.** Chuyến trung chuyển được ghi nhận ở hai đầu kho:

| Bước | Ai làm | Ghi lại gì |
|---|---|---|
| Tạo chuyến, quét các kiện lên xe | Manager kho gửi | Danh sách đơn, biển số xe, tên và SĐT tài xế |
| Niêm phong, xuất kho | Manager kho gửi | Mã niêm phong, số kiện, giờ xuất |
| Nhận chuyến | Manager kho nhận | Đối chiếu mã niêm phong, quét từng kiện, giờ nhận |
| Lệch số kiện | Manager kho nhận | Kiện không quét được thì đánh dấu thất lạc và lập hồ sơ vận hành |

Lý do: tài xế chỉ có 2 thao tác (xuất và nhận), và cả hai đều diễn ra tại kho nơi có Manager. Thêm role thì phải làm thêm đăng nhập, phân quyền, giao diện và phần báo cáo riêng, trong khi đề bài chỉ yêu cầu Guest, User, Manager, Admin và Shipper. Thực tế nhiều hãng cũng thuê tài xế tuyến trục từ nhà xe đối tác và chỉ kiểm soát hàng bằng **mã niêm phong + quét kiện ở hai đầu**.

Hướng phát triển nếu có thời gian: thêm role `DRIVER` (hoặc loại shipper "tuyến trục") với app chỉ có 3 chức năng là xem chuyến được giao, xác nhận đã nhận hàng lên xe, cập nhật vị trí hoặc trạng thái trên đường.
| `route_tariff` | origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text | Phụ phí theo tuyến, Admin cấu hình |

`Order.shipper` vẫn giữ nghĩa "shipper đang chịu trách nhiệm hiện tại": lúc đầu là shipper lấy hàng, sang chặng cuối là shipper giao hàng. Ai làm chặng nào thì ghi riêng trong `order_route`.

## 5. Thuật toán gán đơn cho shipper

### 5.1 Ràng buộc cứng (server kiểm tra, sửa đúng lỗi TP.HCM–Hà Nội)

Một shipper chỉ được gán cho một đơn khi thỏa **tất cả** điều kiện:

1. Shipper đang hoạt động, không bị khóa, đúng vai trò SHIPPER (đã có).
2. **Đúng chi nhánh của chặng:**
   - Chặng lấy hàng (`stage = PICKUP`): `staff_branch(shipper) = origin_branch`.
   - Chặng giao hàng (`stage = DEST_HUB` hoặc đơn nội tỉnh): `staff_branch(shipper) = dest_branch`.
3. Manager thao tác phải thuộc **chính chi nhánh đó** (Admin thì được mọi chi nhánh).
4. **Không vượt sức chứa:** số đơn shipper đang giữ (chờ lấy, đã lấy, đang giao) nhỏ hơn `maxActiveOrders` trong Chính sách vận hành. Đề xuất mặc định là 15.
5. Đơn đủ điều kiện thanh toán (đã có).

Thiếu bất kỳ điều kiện nào thì báo lỗi rõ ràng. Hệ thống không âm thầm gán cho người khác.

### 5.2 Xếp hạng gợi ý (chọn ai trong số shipper hợp lệ)

Với mỗi đơn, lọc các shipper qua 5.1, rồi sắp xếp theo thứ tự:

1. **Khu vực phụ trách:** điểm cần đến (quận lấy hàng ở chặng đầu, quận giao ở chặng cuối) nằm trong khu vực phụ trách của shipper thì xếp trước. Nếu không ai phụ trách quận đó, xếp tiếp những shipper phụ trách **quận lân cận**. Khu vực do **Admin/Manager gán cố định** (bảng `shipper_zone`, xem 5.5).
2. **Ít việc nhất:** số đơn đang giữ thấp hơn thì đứng trước.
3. **Công bằng:** ít đơn được giao trong hôm nay hơn thì đứng trước.
4. Hòa nhau thì lấy id nhỏ hơn, để kết quả lặp lại được khi test.

Độ phức tạp O(số đơn × số shipper của chi nhánh), quy mô vài chục người nên không đáng kể.

### 5.3 Hai chế độ trên trang Phân công

- **Gợi ý rồi xác nhận (mặc định):** mỗi đơn chờ có sẵn shipper đứng đầu được chọn trước trong ô chọn, cạnh tên mỗi shipper hiện "đang giữ X/15 đơn". Lý do tự điền "Gợi ý: cùng quận Bình Thạnh, ít việc nhất", Manager sửa được. Manager vẫn bấm xác nhận nên vẫn có nhật ký và trách nhiệm.
- **Phân công tự động hàng loạt:** nút "Phân công tất cả theo gợi ý" cho các đơn đang chờ của chi nhánh. Hệ thống duyệt đơn theo thứ tự cũ trước, mỗi lần gán thì cộng tải cho shipper vừa nhận rồi mới xét đơn tiếp theo. Đơn nào không còn shipper hợp lệ thì để lại và báo "Hết shipper còn sức chứa".

### 5.4 Sắp xếp thứ tự đi cho shipper (danh sách "Lộ trình hôm nay")

Đây là bài toán khác với 5.2: đơn đã được gán, giờ xếp **đi đơn nào trước**.

1. **Chia nhóm theo độ gấp:**
   1. Hỏa tốc, và đơn có giờ hẹn sắp tới (giao lại đến hẹn).
   2. Đơn sắp quá hạn theo Chính sách vận hành (hạn lấy hoặc hạn giao).
   3. Còn lại.
2. **Trong từng nhóm, xếp theo láng giềng gần nhất:** xuất phát từ **kho chi nhánh**, đi tới điểm gần nhất, rồi từ đó tới điểm gần nhất tiếp theo... Khoảng cách tính bằng công thức Haversine giữa **tọa độ trung tâm quận** (cột `lat`, `lng` trong `province_area`, nạp sẵn). Không gọi Google Maps API nên không tốn phí, không cần khóa API.
3. **Đơn nội tỉnh shipper vừa lấy vừa giao:** điểm lấy luôn đứng trước điểm giao của cùng đơn.

Đây là thuật toán xấp xỉ (heuristic) của bài toán định tuyến xe (VRP). Kết quả không tối ưu tuyệt đối nhưng đủ hợp lý, dễ giải thích khi bảo vệ. Hướng phát triển là dùng Google OR-Tools.

### 5.5 Khu vực phụ trách thay cho nơi ở của shipper

Bảng mới `shipper_zone(shipper_id, district_code, primary)`: mỗi shipper có 1 quận chính và có thể thêm vài quận phụ, **cùng tỉnh với chi nhánh**. Admin hoặc Manager chi nhánh gán khi cấp tài khoản.

**Không dùng địa chỉ nhà riêng của shipper để tính phân đơn**, vì:
- Shipper bắt đầu ca từ **kho chi nhánh** (nhận hàng, nhận việc), không phải từ nhà.
- Địa chỉ nhà là **dữ liệu cá nhân nhạy cảm**. Đưa vào thuật toán thì Manager nhìn thấy, rủi ro lộ thông tin.
- Nơi ở không phản ánh vị trí lúc làm việc. Còn muốn theo dõi vị trí thật thì phải có GPS từ app điện thoại, vượt phạm vi đồ án.
- Khu vực phụ trách cố định giúp shipper **thuộc đường, quen khách**. Thực tế các hãng cũng chia tuyến cố định theo phường hoặc quận.

Địa chỉ liên hệ của shipper vẫn lưu trong hồ sơ nhân sự như hiện tại, chỉ không dùng để phân đơn.

## 6. Cước theo khoảng cách và tuyến

"Tính phí theo khoảng cách" nằm trong danh sách chức năng thầy đã duyệt, nên **giữ lại** cho đơn nội tỉnh:

```
Cước = Cước cơ bản (dịch vụ) + Phí mỗi kg × Khối lượng
     + Phụ phí khoảng cách (nội tỉnh: km tự tính từ tâm quận lấy → tâm quận giao, bậc phí như hiện tại)
     + Phụ phí tuyến       (liên tỉnh: route_tariff theo cặp chi nhánh)
```

Bảng phụ phí tuyến đề xuất (Admin chỉnh được):

| Tuyến | Tiêu chuẩn | Nhanh | Hỏa tốc | Thời gian dự kiến (Tiêu chuẩn / Nhanh) |
|---|---|---|---|---|
| Nội tỉnh | 0 | 0 | 0 | 1–2 ngày / trong ngày |
| TP.HCM ↔ Đà Nẵng | 15.000 | 25.000 | không nhận | 3–4 ngày / 2 ngày |
| Hà Nội ↔ Đà Nẵng | 15.000 | 25.000 | không nhận | 3–4 ngày / 2 ngày |
| TP.HCM ↔ Hà Nội | 25.000 | 40.000 | không nhận | 4–5 ngày / 2–3 ngày |

- **Hỏa tốc chỉ nội tỉnh**, khớp với quy tắc "Hỏa tốc trong 30 km" đang có.
- Trang Ước tính cước và lúc tạo đơn **dùng chung một hàm tính**, nên hết tình trạng ước tính một giá, tạo đơn ra giá khác.
- Đơn **nội tỉnh**: khoảng cách tính tự động bằng Haversine giữa tọa độ tâm quận, người dùng không nhập km. Áp bậc phụ phí và hệ số dịch vụ đang có.
- Đơn **liên tỉnh**: không cộng phụ phí km (tránh ra khoảng 246.000 đ cho TP.HCM–Hà Nội), chỉ cộng phụ phí tuyến.

## 7. Phân việc theo thành viên

### Thọ (TV1, Guest + User)

| # | Việc | Chi tiết | Phụ thuộc |
|---|---|---|---|
| 1.1 | Địa chỉ có cấu trúc khi tạo đơn | Thêm ô chọn Tỉnh/thành và Quận/huyện (3 tỉnh) cho địa chỉ lấy và địa chỉ giao. Ô "Số nhà, đường" giữ dạng chữ. Server kiểm tra quận thuộc tỉnh, tỉnh có chi nhánh phục vụ | 3.1 (danh mục quận) |
| 1.2 | Sổ địa chỉ và giỏ đơn | `SavedAddress` và `DraftOrder` lưu thêm tỉnh/quận, qua bảng phụ hoặc cột mới, quyết định cùng 3.1. Địa chỉ cũ chưa có tỉnh/quận thì bắt người dùng chọn lại khi dùng | 1.1 |
| 1.3 | Cước thống nhất | Trang Ước tính cước đổi ô nhập km thành chọn Tỉnh + Quận gửi và nhận, hiện số km tự tính. Gọi chung hàm tính cước với lúc tạo đơn. Hỏa tốc bị ẩn khi khác tỉnh hoặc quá 30 km | 3.6 |
| 1.4 | Tạo `order_route` khi tạo đơn | Trong cùng transaction tạo đơn: xác định chi nhánh gửi/nhận từ tỉnh, tạo dòng `order_route` (stage = PICKUP) | 3.1 |
| 1.5 | Hành trình cho người gửi và Guest | Chi tiết đơn, trang tra cứu và hành trình hiện thêm các mốc "Nhập kho SGN", "Đang trung chuyển SGN → HAN", "Nhập kho HAN". Hiện chi nhánh gửi/nhận và thời gian dự kiến | 3.4 |
| 1.6 | Trợ lý Groq và email | Trả lời "đơn đang ở đâu" có kèm kho hiện tại. Email cập nhật đơn thêm các mốc kho | 1.5 |

### Tài (TV2, Shipper)

| # | Việc | Chi tiết | Phụ thuộc |
|---|---|---|---|
| 2.1 | Chỉ thấy việc của chi nhánh mình | Danh sách việc, lộ trình, thông báo chỉ lấy đơn mà shipper được gán **đúng chặng**. Hiện tên chi nhánh trên trang shipper | 3.1, 3.2 |
| 2.2 | Chặng lấy hàng liên tỉnh | Sau "Đã lấy hàng", shipper bấm **"Bàn giao vào kho SGN"** thay vì "Đang giao". Có thể quét QR tại kho. Đơn chuyển sang `stage = ORIGIN_HUB`, shipper không còn giữ đơn đó | 3.3 |
| 2.3 | Chặng giao hàng | Shipper chi nhánh nhận được phân công ở `stage = DEST_HUB`, đi tiếp như hiện tại: Đang giao → bằng chứng → Giao thành công/thất bại | 3.3 |
| 2.4 | Đối soát COD theo chặng | Tiền COD do **shipper giao hàng** thu, nên phiếu nộp tiền thuộc chi nhánh nhận. Shipper lấy hàng không thu COD | 3.5 |
| 2.5 | Thống kê theo chặng | Thống kê tách "đơn đã lấy" và "đơn đã giao". Xuất CSV có cột chặng | 2.2, 2.3 |
| 2.6 | Giao thất bại liên tỉnh | Giao thất bại thì đơn về lại `DEST_HUB`. Hoàn hàng liên tỉnh xem mục 8 | 3.4 |

### Thắng (TV3, Manager + Admin, chịu trách nhiệm DB)

| # | Việc | Chi tiết | Phụ thuộc |
|---|---|---|---|
| 3.1 | Migration `UpgradeAivenBranches` | Tạo các bảng ở mục 4, nạp 3 chi nhánh, danh mục quận/huyện 3 tỉnh, `route_tariff` mặc định. Có `--check`, `--print-schema`, `--apply` như các công cụ cũ. Một người chạy, cả nhóm tắt app | Chốt mục 10 |
| 3.2 | Admin: chi nhánh và nhân sự | Trang CRUD chi nhánh. Trang Quản lý tài khoản thêm ô "Chi nhánh" cho SHIPPER/MANAGER (bắt buộc với 2 vai trò này) | 3.1 |
| 3.3 | Phân công theo thuật toán mục 5 | Gộp hai service phân công thành một (bỏ `ManagerDispatchServiceImpl` đang không được dùng). Thêm ràng buộc cứng 5.1, gợi ý 5.2, hiện tải việc trong ô chọn, nút phân công hàng loạt. Thêm `maxActiveOrders` vào Chính sách vận hành | 3.1, 3.2 |
| 3.4 | Màn hình kho | "Nhập kho" (nhận hàng từ shipper lấy hàng), "Tạo chuyến trung chuyển" (gom đơn cùng chi nhánh đến), "Xuất kho", "Nhận chuyến" ở kho đến. Mọi thao tác ghi `OrderStatusHistory` và nhật ký | 3.1 |
| 3.5 | Phạm vi dữ liệu theo chi nhánh | Manager chỉ thấy đơn có `origin_branch` hoặc `dest_branch` là chi nhánh mình, áp cho tra soát, cảnh báo, khiếu nại, đối soát COD, báo cáo. Admin xem tất cả, có ô lọc chi nhánh | 3.1, 3.2 |
| 3.6 | Cước theo tuyến | Trang Admin cấu hình `route_tariff`. Hàm tính cước dùng chung `quote(service, kg, originBranch, destBranch)` cho cả TV1 | 3.1 |
| 3.7 | Báo cáo theo chi nhánh | Báo cáo vận hành và hiệu suất shipper lọc theo chi nhánh. Biểu đồ đơn theo tuyến | 3.5 |
| 3.8 | Chuyển dữ liệu cũ | Công cụ gán chi nhánh cho đơn cũ (xem mục 9), gán `shipper1..3` và `manager1` vào SGN, tạo thêm tài khoản demo cho HAN và DAD | 3.1, 3.2 |

## 8. Hoàn hàng liên tỉnh (làm sau, nếu kịp)

Phiên bản tối thiểu: đơn liên tỉnh giao thất bại vượt giới hạn thì Manager chi nhánh nhận lập hồ sơ "Chuyển hoàn" như hiện tại. Đơn đi **chuyến trung chuyển ngược** về kho gửi, rồi shipper chi nhánh gửi giao trả người gửi (đánh dấu RETURNED). Dùng lại màn hình kho ở 3.4 với chiều ngược lại, không cần bảng mới.

## 9. Xử lý dữ liệu cũ

- Đơn có địa chỉ chứa "TP.HCM / Thủ Đức / Quận x" thì gán SGN. "Hà Nội" thì HAN. "Đà Nẵng" thì DAD.
- Đơn ngoài 3 tỉnh (Huế, Nha Trang, Cần Thơ...) chỉ còn là dữ liệu lịch sử. Đơn đã kết thúc giữ nguyên, ghi chi nhánh gần nhất để báo cáo không lỗi. Đơn **đang chạy** ngoài vùng thì liệt kê ra để Manager xử lý tay.
- Công cụ chạy `--check` trước để in bảng ánh xạ cho cả nhóm duyệt, rồi mới `--apply`.
- Tài khoản demo đề xuất: SGN có `manager1`, `shipper1`, `shipper2`. HAN có `manager_han`, `shipper3`, `shipper_han2`. DAD có `manager_dad`, `shipper_dad1`. Như vậy mỗi chi nhánh có ít nhất 1 Manager, và có chi nhánh có 2 shipper để thử thuật toán chọn người ít việc.

## 10. Câu hỏi cần chốt trước khi code

1. Có đồng ý **không sửa enum `OrderStatus`**, dùng `order_route.stage` như mục 3 không?
2. ~~Quận phụ trách tự suy hay gán cố định?~~ **Đã chốt: gán cố định bằng `shipper_zone` (mục 5.5).** Còn cần chốt: Admin gán, hay Manager chi nhánh cũng được gán?
3. `maxActiveOrders` mặc định bao nhiêu (đề xuất 15)?
4. Phụ phí tuyến ở mục 6 có hợp lý không?
5. Có làm hoàn hàng liên tỉnh (mục 8) trong đợt này không, hay để ghi vào phần hướng phát triển?
6. Ai chạy migration và vào lúc nào (đề xuất Thắng, sau khi cả nhóm review `--print-schema`)?

## 11. Thứ tự làm

| Giai đoạn | Việc | Ai | Điều kiện xong |
|---|---|---|---|
| 0. Chốt | Trả lời mục 10, sửa tài liệu này | Cả nhóm | Tài liệu được duyệt |
| 1. Nền | 3.1, 3.2, 3.6 (phần hàm tính cước) | Thắng | Migration chạy trên Aiven, có chi nhánh và nhân sự đã gán |
| 2. Song song | TV1: 1.1–1.4 · TV2: 2.1–2.3 · TV3: 3.3–3.5 | Từng người | Mỗi nhánh qua test riêng |
| 3. Ghép | 1.5, 1.6, 2.4, 2.5, 3.7, 3.8 | Từng người | Chạy được 3 kịch bản dưới đây |
| 4. Tùy chọn | Mục 8, 2.6 | Ai còn thời gian | |

Trong giai đoạn 2, TV1 và TV2 có thể làm giao diện trước, dùng dữ liệu giả, rồi nối vào service của Thắng khi 3.1 xong.

## 12. Kịch bản kiểm thử bắt buộc

1. **Nội tỉnh SGN → SGN:** Manager SGN thấy gợi ý shipper SGN ít việc nhất. Shipper đó lấy và giao trọn. Manager HAN **không thấy** đơn này.
2. **Liên tỉnh SGN → HAN:** shipper SGN lấy hàng và bàn giao kho SGN. Manager SGN tạo chuyến và xuất kho. Manager HAN nhận chuyến. Phân công shipper HAN giao. Người gửi thấy đủ các mốc. Cước có phụ phí tuyến, khớp trang ước tính.
3. **Chặn sai chi nhánh:** gửi form phân công shipper SGN cho chặng giao ở HAN (sửa request bằng tay) thì server từ chối.
4. **Sức chứa:** shipper đủ 15 đơn thì không còn trong gợi ý. Phân công hàng loạt bỏ qua người đó.
5. **Ngoài vùng:** tạo đơn giao Cần Thơ thì bị từ chối với thông báo rõ ràng.
