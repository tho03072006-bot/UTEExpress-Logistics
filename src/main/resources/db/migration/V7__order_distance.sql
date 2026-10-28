-- Cước tính thêm phụ phí khoảng cách đường chim bay giữa địa chỉ lấy hàng và giao hàng.
-- Chỉ THÊM 2 cột cho phép NULL: đơn cũ để trống, không đổi giá/dữ liệu đơn cũ.
-- Dùng UpgradeAivenOrderDistance --check / --apply để kiểm tra trước và chạy lại an toàn.
ALTER TABLE shipment_order ADD COLUMN distance_km DECIMAL(7,2) NULL;
ALTER TABLE shipment_order ADD COLUMN distance_fee DECIMAL(12,0) NULL;
