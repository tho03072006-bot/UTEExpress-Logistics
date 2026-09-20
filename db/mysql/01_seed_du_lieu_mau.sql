-- ============================================================================
-- UTEExpress - du lieu mau cho database chung tren Aiven MySQL
-- ============================================================================
--
-- DUNG DE LAM GI
--   Database Aiven chi co schema trong nen ca nhom khong demo duoc gi. Script nay
--   dung mot bo du lieu co quy mo nhu website dang chay that: nhieu tai khoan,
--   80 van don trai deu 3 thang gan nhat voi du cac trang thai, kem thanh toan
--   va danh gia. Nho vay phan trang, bo loc, thong ke Shipper va bang dieu khien
--   Manager deu co so lieu that de nhin.
--
-- LUU Y: cac script nam truc tiep trong db/ la cu phap SQL SERVER cua database cu,
--   KHONG chay duoc tren MySQL. Chi thu muc db/mysql/ moi dung cho Aiven.
--
-- CHAY LAI DUOC NHIEU LAN
--   Khoa theo cot unique (username, code, tracking_code) va kiem tra NOT EXISTS
--   truoc khi them, nen chay lai khong nhan doi du lieu.
--
-- KHONG GAN CUNG ID
--   Cac bang deu AUTO_INCREMENT va database da co san tai khoan admin cua Thang.
--   Script tra cuu id qua username / tracking_code de khong de len du lieu nguoi khac.
--
-- MAT KHAU
--   Tat ca tai khoan: Test@12345
--   Rieng newuser1   : MatKhauMoi@2026
--   Ma bam BCrypt sinh bang chinh BCryptPasswordEncoder cua ung dung.
-- ============================================================================

-- ===== 1. Tai khoan =====
-- enabled = 1 la da kich hoat OTP, dang nhap duoc ngay.
-- Rieng chuakichhoat de 0 de thu duong dang nhap bi tu choi.
INSERT INTO app_user (username, password, full_name, email, phone, role, enabled, created_at)
VALUES
  -- Nguoi gui hang
  ('thotest',      '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Nguyễn Văn Thọ',    'thotest@example.com',      '0901234567', 'USER',    1, DATE_SUB(NOW(6), INTERVAL 120 DAY)),
  ('newuser1',     '$2a$10$5p3HVFVFnBsxbAIxEMR9puvg8rIjMsFH0VX5SLCRe5ZcIKj3SYQEe',
   'Người Dùng Mới',    'newuser1@example.com',     NULL,         'USER',    1, DATE_SUB(NOW(6), INTERVAL 30 DAY)),
  ('chuakichhoat', '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Chưa Kích Hoạt',    'chuakichhoat@example.com', NULL,         'USER',    0, DATE_SUB(NOW(6), INTERVAL 5 DAY)),
  ('huongshop',    '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Trần Thị Hương',    'huongshop@example.com',    '0902345678', 'USER',    1, DATE_SUB(NOW(6), INTERVAL 100 DAY)),
  ('minhstore',    '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Lê Quang Minh',     'minhstore@example.com',    '0903456789', 'USER',    1, DATE_SUB(NOW(6), INTERVAL 85 DAY)),
  ('lanboutique',  '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Phạm Thị Lan',      'lanboutique@example.com',  '0904567890', 'USER',    1, DATE_SUB(NOW(6), INTERVAL 60 DAY)),
  -- Shipper
  ('shipper1',     '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Trần Văn Dũng',     'shipper1@uteexpress.test', '0909111222', 'SHIPPER', 1, DATE_SUB(NOW(6), INTERVAL 150 DAY)),
  ('shipper2',     '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Nguyễn Hữu Phát',   'shipper2@uteexpress.test', '0909222333', 'SHIPPER', 1, DATE_SUB(NOW(6), INTERVAL 140 DAY)),
  ('shipper3',     '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Võ Thành Long',     'shipper3@uteexpress.test', '0909333444', 'SHIPPER', 1, DATE_SUB(NOW(6), INTERVAL 90 DAY)),
  -- Quan ly
  ('manager1',     '$2a$10$Yt.pNX5AkCKM8IkiIZphoOhUBEJ/ufe0yETdjL3b14zFizixxKuLm',
   'Lê Thị Quản Lý',    'manager1@uteexpress.test', '0908000111', 'MANAGER', 1, DATE_SUB(NOW(6), INTERVAL 150 DAY))
ON DUPLICATE KEY UPDATE
  full_name = VALUES(full_name),
  phone     = VALUES(phone),
  role      = VALUES(role),
  enabled   = VALUES(enabled);

-- ===== 2. Ma giam gia =====
-- HETHAN va DATAT co y de sai: mot ma qua han, mot ma bi tat - dung de thu duong
-- bao loi khi nguoi dung nhap ma khong dung duoc.
INSERT INTO promo_code (code, description, discount_type, discount_value,
                        max_discount_amount, min_order_amount,
                        start_at, end_at, usage_limit, used_count, active,
                        created_at, updated_at)
VALUES
  ('UTE10',      'Giảm 10% cước, tối đa 20.000đ',     'PERCENT',      10,
   20000, NULL,  DATE_SUB(NOW(6), INTERVAL 60 DAY), DATE_ADD(NOW(6), INTERVAL 6 MONTH), 100, 23, 1, NOW(6), NOW(6)),
  ('FREESHIP15', 'Giảm 15.000đ cho đơn từ 30.000đ',   'FIXED_AMOUNT', 15000,
   NULL,  30000, DATE_SUB(NOW(6), INTERVAL 45 DAY), DATE_ADD(NOW(6), INTERVAL 6 MONTH), 50,  12, 1, NOW(6), NOW(6)),
  ('TANTHU20',   'Giảm 20% cho khách lần đầu gửi hàng', 'PERCENT',    20,
   30000, NULL,  DATE_SUB(NOW(6), INTERVAL 30 DAY), DATE_ADD(NOW(6), INTERVAL 3 MONTH), 200, 8,  1, NOW(6), NOW(6)),
  ('HETHAN',     'Mã đã hết hạn (dùng để kiểm thử)',  'PERCENT',      50,
   NULL,  NULL,  DATE_SUB(NOW(6), INTERVAL 90 DAY), DATE_SUB(NOW(6), INTERVAL 30 DAY),  NULL, 0, 1, NOW(6), NOW(6)),
  ('DATAT',      'Mã đã tắt (dùng để kiểm thử)',      'FIXED_AMOUNT', 5000,
   NULL,  NULL,  DATE_SUB(NOW(6), INTERVAL 20 DAY), DATE_ADD(NOW(6), INTERVAL 6 MONTH), NULL, 0, 0, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
  description         = VALUES(description),
  discount_type       = VALUES(discount_type),
  discount_value      = VALUES(discount_value),
  max_discount_amount = VALUES(max_discount_amount),
  min_order_amount    = VALUES(min_order_amount),
  start_at            = VALUES(start_at),
  end_at              = VALUES(end_at),
  usage_limit         = VALUES(usage_limit),
  used_count          = VALUES(used_count),
  active              = VALUES(active),
  updated_at          = NOW(6);

-- ===== 3. So dia chi da luu =====
-- Bang nay khong co rang buoc unique nen phai tu kiem tra truoc khi them,
-- neu khong chay lai script se sinh dia chi trung.
INSERT INTO saved_address (user_id, label, contact_name, contact_phone, address_line,
                           address_type, is_default, created_at, updated_at)
SELECT u.id, d.label, d.contact_name, d.contact_phone, d.address_line,
       d.address_type, d.is_default, NOW(6), NOW(6)
FROM (
      SELECT 'thotest'   AS username, 'Kho quận 9'     AS label, 'Trần Thị Bích' AS contact_name,
             '0912345678' AS contact_phone, '250 Lê Văn Việt, TP Thủ Đức, TP.HCM' AS address_line,
             'SENDER'     AS address_type, 1 AS is_default
UNION SELECT 'thotest',   'Nhà riêng',      'Nguyễn Văn Thọ', '0901234567',
             '12 Võ Văn Ngân, TP Thủ Đức, TP.HCM',  'SENDER',   0
UNION SELECT 'thotest',   'Nhà mẹ ở Huế',   'Lê Thị Hồng',    '0987654321',
             '5 Đường Hùng Vương, TP Huế',          'RECEIVER', 1
UNION SELECT 'thotest',   'Văn phòng Q1',   'Phạm Quốc Đạt',  '0911222333',
             '88 Nguyễn Huệ, Quận 1, TP.HCM',       'RECEIVER', 0
UNION SELECT 'huongshop', 'Shop Gò Vấp',    'Trần Thị Hương', '0902345678',
             '145 Quang Trung, Gò Vấp, TP.HCM',     'SENDER',   1
UNION SELECT 'minhstore', 'Kho Bình Thạnh', 'Lê Quang Minh',  '0903456789',
             '60 Xô Viết Nghệ Tĩnh, Bình Thạnh, TP.HCM', 'SENDER', 1
     ) AS d
JOIN app_user u ON u.username = d.username
WHERE NOT EXISTS (SELECT 1 FROM saved_address s
                  WHERE s.user_id = u.id AND s.label = d.label);

-- ===== 4. Van don =====
-- Sinh 80 don bang CTE de quy thay vi liet ke tay, roi phan bo trang thai theo
-- phan du cua so thu tu. Ty le dat gan giong thuc te: phan lon da giao xong,
-- mot phan dang tren duong, so it huy / that bai / hoan tra.
--
-- Don tu PICKED_UP tro di deu duoc gan shipper, neu khong thi dang nhap shipper
-- se thay trang trong va phan cua TV2 khong demo duoc.
--
-- Cuoc phi tinh dung cong thuc cua ung dung: phi co ban theo loai dich vu cong
-- 3.000d moi kilogam - de so lieu khop voi trang uoc tinh cuoc.
INSERT INTO shipment_order (tracking_code, sender_id, shipper_id,
                            sender_name, sender_address,
                            receiver_name, receiver_phone, receiver_address,
                            weight_kg, service_type, shipping_fee, status,
                            created_at, updated_at)
WITH RECURSIVE day_so (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM day_so WHERE n < 80
),
don AS (
    SELECT
        n,
        CONCAT('UTE25', LPAD(n, 4, '0')) AS tracking_code,
        -- Nguoi gui: phan bo cho 4 tai khoan, thotest giu nhieu nhat de de demo
        CASE n % 6 WHEN 0 THEN 'huongshop' WHEN 1 THEN 'minhstore'
                   WHEN 2 THEN 'lanboutique' ELSE 'thotest' END AS sender_username,
        CASE n % 3 WHEN 0 THEN 'STANDARD' WHEN 1 THEN 'EXPRESS' ELSE 'SUPER_EXPRESS' END AS service_type,
        ROUND(0.5 + (n % 18) * 0.35, 1) AS weight_kg,
        -- Ty le trang thai: 40% da giao, 25% dang di, 15% cho lay, 20% ket thuc xau
        CASE
            WHEN n % 20 IN (0, 1, 2, 3, 4, 5, 6, 7)  THEN 'DELIVERED'
            WHEN n % 20 IN (8, 9, 10)                THEN 'IN_TRANSIT'
            WHEN n % 20 IN (11, 12)                  THEN 'PICKED_UP'
            WHEN n % 20 IN (13, 14, 15)              THEN 'PENDING_PICKUP'
            WHEN n % 20 IN (16, 17)                  THEN 'CANCELLED'
            WHEN n % 20 = 18                         THEN 'FAILED'
            ELSE 'RETURNED'
        END AS status,
        CASE n % 8
            WHEN 0 THEN 'Lê Thị Hồng'    WHEN 1 THEN 'Phạm Quốc Đạt'
            WHEN 2 THEN 'Võ Minh Khang'  WHEN 3 THEN 'Đặng Thu Hà'
            WHEN 4 THEN 'Bùi Thanh Tâm'  WHEN 5 THEN 'Hoàng Văn Nam'
            WHEN 6 THEN 'Ngô Thị Mai'    ELSE 'Vũ Đức Anh'
        END AS receiver_name,
        CASE n % 8
            WHEN 0 THEN '5 Đường Hùng Vương, TP Huế'
            WHEN 1 THEN '88 Nguyễn Huệ, Quận 1, TP.HCM'
            WHEN 2 THEN '12 Trần Phú, TP Đà Nẵng'
            WHEN 3 THEN '45 Lý Thường Kiệt, TP Hà Nội'
            WHEN 4 THEN '7 Nguyễn Trãi, TP Nha Trang'
            WHEN 5 THEN '30 Hai Bà Trưng, TP Cần Thơ'
            WHEN 6 THEN '99 Quang Trung, TP Vũng Tàu'
            ELSE '21 Lê Lợi, TP Buôn Ma Thuột'
        END AS receiver_address,
        CONCAT('09', LPAD(11000000 + n * 137, 8, '0')) AS receiver_phone,
        -- Don so thu tu cang nho thi cang cu: trai deu trong 90 ngay gan nhat
        (90 - n) AS so_ngay_truoc
    FROM day_so
)
SELECT d.tracking_code, u.id,
       CASE WHEN d.status IN ('PENDING_PICKUP', 'CANCELLED') THEN NULL ELSE sp.id END,
       u.full_name,
       CASE d.sender_username
           WHEN 'huongshop' THEN '145 Quang Trung, Gò Vấp, TP.HCM'
           WHEN 'minhstore' THEN '60 Xô Viết Nghệ Tĩnh, Bình Thạnh, TP.HCM'
           WHEN 'lanboutique' THEN '3 Nguyễn Oanh, Gò Vấp, TP.HCM'
           ELSE '250 Lê Văn Việt, TP Thủ Đức, TP.HCM'
       END,
       d.receiver_name, d.receiver_phone, d.receiver_address,
       d.weight_kg, d.service_type,
       -- phi co ban + 3.000d/kg, lam tron ve so nguyen dong
       ROUND(CASE d.service_type WHEN 'STANDARD' THEN 15000
                                 WHEN 'EXPRESS' THEN 25000
                                 ELSE 40000 END + d.weight_kg * 3000, 0),
       d.status,
       DATE_SUB(NOW(6), INTERVAL d.so_ngay_truoc DAY),
       -- Don da ket thuc thi cap nhat lan cuoi sau khi tao vai ngay, don dang chay
       -- thi vua cap nhat gan day
       CASE WHEN d.status IN ('DELIVERED', 'FAILED', 'RETURNED', 'CANCELLED')
            THEN DATE_SUB(NOW(6), INTERVAL (d.so_ngay_truoc - 2) DAY)
            ELSE DATE_SUB(NOW(6), INTERVAL (d.n % 12) HOUR)
       END
FROM don d
JOIN app_user u ON u.username = d.sender_username
LEFT JOIN app_user sp ON sp.username = CASE d.n % 3
                                           WHEN 0 THEN 'shipper1'
                                           WHEN 1 THEN 'shipper2'
                                           ELSE 'shipper3'
                                       END
ON DUPLICATE KEY UPDATE
  status     = VALUES(status),
  shipper_id = VALUES(shipper_id),
  updated_at = VALUES(updated_at);

-- ===== 5. Ban ghi thanh toan =====
-- Moi van don deu co dung mot ban ghi thanh toan (rang buoc nghiep vu cua ung dung).
-- Don da giao thi da tra tien, don khac con no.
-- Mot phan don duoc ap ma giam gia de trang danh sach co dong "Thuc tra ...".
INSERT INTO order_payment (order_id, method, status, amount, discount_amount,
                           promo_code_id, paid_at, created_at, updated_at)
SELECT o.id,
       CASE CAST(SUBSTRING(o.tracking_code, 6) AS UNSIGNED) % 5
           WHEN 0 THEN 'VNPAY' WHEN 1 THEN 'MOMO' ELSE 'COD'
       END,
       CASE WHEN o.status = 'DELIVERED' THEN 'PAID' ELSE 'UNPAID' END,
       -- So tien thuc tra = cuoc phi tru phan giam
       o.shipping_fee - CASE WHEN CAST(SUBSTRING(o.tracking_code, 6) AS UNSIGNED) % 4 = 0
                             THEN LEAST(ROUND(o.shipping_fee * 0.1, 0), 20000) ELSE 0 END,
       CASE WHEN CAST(SUBSTRING(o.tracking_code, 6) AS UNSIGNED) % 4 = 0
            THEN LEAST(ROUND(o.shipping_fee * 0.1, 0), 20000) ELSE 0 END,
       CASE WHEN CAST(SUBSTRING(o.tracking_code, 6) AS UNSIGNED) % 4 = 0
            THEN (SELECT p.id FROM promo_code p WHERE p.code = 'UTE10') ELSE NULL END,
       CASE WHEN o.status = 'DELIVERED' THEN o.updated_at ELSE NULL END,
       o.created_at, o.updated_at
FROM shipment_order o
WHERE o.tracking_code LIKE 'UTE25%'
  AND NOT EXISTS (SELECT 1 FROM order_payment p WHERE p.order_id = o.id);

-- ===== 6. Danh gia dich vu =====
-- Chi don da giao thanh cong moi danh gia duoc. Lay khoang mot nua so don da giao
-- de vua co don "da danh gia" vua con don "chua danh gia" ma thu nut Danh gia.
-- Noi dung deu tren 50 ky tu cho dung rang buoc cua ServiceReviewRequest.
INSERT INTO service_review (order_id, user_id, rating, content, media_path, media_type,
                            created_at, updated_at)
SELECT o.id, o.sender_id,
       CASE CAST(SUBSTRING(o.tracking_code, 6) AS UNSIGNED) % 10
           WHEN 0 THEN 3 WHEN 3 THEN 4 WHEN 6 THEN 4 ELSE 5
       END,
       CASE CAST(SUBSTRING(o.tracking_code, 6) AS UNSIGNED) % 6
           WHEN 0 THEN 'Hàng tới sớm hơn dự kiến một ngày, thùng carton còn nguyên seal, không có dấu hiệu bị rạch hay móp. Shipper thái độ lịch sự.'
           WHEN 1 THEN 'Shipper gọi điện trước khi tới, giao đúng khung giờ đã hẹn. Hàng gói kỹ, nguyên vẹn không móp méo. Rất hài lòng với dịch vụ.'
           WHEN 2 THEN 'Đặt giao nhanh và hàng tới đúng hẹn, không phát sinh phụ phí nào. Theo dõi hành trình trên web rất rõ ràng, biết hàng đang ở đâu.'
           WHEN 3 THEN 'Mình bán hàng online nên tốc độ giao rất quan trọng. Dùng UTEExpress thấy khách phản hồi tích cực hơn hẳn vì đơn đến đúng hẹn.'
           WHEN 4 THEN 'Giao hàng ổn, đúng ngày. Có điều lần này shipper tới hơi muộn so với giờ hẹn một chút, nhưng có gọi báo trước nên cũng không sao.'
           ELSE 'Cước phí hợp lý so với các bên khác, lại tra cứu được hành trình từng bước. Sẽ tiếp tục dùng cho các đơn hàng sau của shop.'
       END,
       NULL, NULL, o.updated_at, o.updated_at
FROM shipment_order o
WHERE o.tracking_code LIKE 'UTE25%'
  AND o.status = 'DELIVERED'
  AND CAST(SUBSTRING(o.tracking_code, 6) AS UNSIGNED) % 2 = 0
  AND NOT EXISTS (SELECT 1 FROM service_review r WHERE r.order_id = o.id);
