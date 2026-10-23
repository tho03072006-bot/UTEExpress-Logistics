-- Migration chạy tay của UTEExpress; người vận hành review trước khi tự chạy --apply.
-- Khu vực giao nhận nội bộ theo địa danh cũ; tọa độ tâm xấp xỉ, dùng cho sắp lộ trình.
-- Chỉ thêm bảng, thêm max_active_orders DEFAULT 15 và INSERT danh mục còn thiếu.
-- Công cụ kiểm tra cấu trúc/khóa và dữ liệu có sẵn trước khi ghi; không chạy SQL này lên schema chưa review.
CREATE TABLE IF NOT EXISTS branch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    province_code VARCHAR(10) NOT NULL,
    hub_address VARCHAR(255) NOT NULL,
    hub_area_code VARCHAR(40) NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_branch_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS province_area (
    id BIGINT NOT NULL AUTO_INCREMENT,
    province_code VARCHAR(10) NOT NULL,
    area_code VARCHAR(40) NOT NULL,
    area_name VARCHAR(100) NOT NULL,
    branch_id BIGINT NOT NULL,
    lat DECIMAL(9,6) NOT NULL,
    lng DECIMAL(9,6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_area_code UNIQUE (area_code),
    CONSTRAINT fk_area_branch FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_area_branch (branch_id),
    CONSTRAINT ck_area_coordinates CHECK (lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS area_neighbor (
    area_code VARCHAR(40) NOT NULL,
    neighbor_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (area_code, neighbor_code),
    CONSTRAINT fk_neighbor_area FOREIGN KEY (area_code) REFERENCES province_area(area_code) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_neighbor_other FOREIGN KEY (neighbor_code) REFERENCES province_area(area_code) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_neighbor_reverse (neighbor_code),
    CONSTRAINT ck_area_neighbor_distinct CHECK (area_code <> neighbor_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS staff_branch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_staff_branch_user UNIQUE (user_id),
    CONSTRAINT fk_staff_branch_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_staff_branch_branch FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_staff_branch_actor FOREIGN KEY (assigned_by) REFERENCES app_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_staff_branch (branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS shipper_zone (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shipper_id BIGINT NOT NULL,
    area_code VARCHAR(40) NOT NULL,
    is_primary BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_shipper_zone UNIQUE (shipper_id, area_code),
    CONSTRAINT fk_zone_shipper FOREIGN KEY (shipper_id) REFERENCES app_user(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_zone_area FOREIGN KEY (area_code) REFERENCES province_area(area_code) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_route (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    origin_branch_id BIGINT NOT NULL,
    dest_branch_id BIGINT NOT NULL,
    pickup_area_code VARCHAR(40) NOT NULL,
    delivery_area_code VARCHAR(40) NOT NULL,
    stage ENUM('PICKUP','ORIGIN_HUB','LINEHAUL','DEST_HUB','LAST_MILE') NOT NULL,
    pickup_shipper_id BIGINT NULL,
    delivery_shipper_id BIGINT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_route_order UNIQUE (order_id),
    CONSTRAINT fk_route_order FOREIGN KEY (order_id) REFERENCES shipment_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_route_origin FOREIGN KEY (origin_branch_id) REFERENCES branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_route_dest FOREIGN KEY (dest_branch_id) REFERENCES branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_route_pickup_area FOREIGN KEY (pickup_area_code) REFERENCES province_area(area_code) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_route_delivery_area FOREIGN KEY (delivery_area_code) REFERENCES province_area(area_code) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_route_pickup_shipper FOREIGN KEY (pickup_shipper_id) REFERENCES app_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_route_delivery_shipper FOREIGN KEY (delivery_shipper_id) REFERENCES app_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_route_origin_stage (origin_branch_id, stage),
    INDEX idx_route_dest_stage (dest_branch_id, stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS linehaul_trip (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    from_branch_id BIGINT NOT NULL,
    to_branch_id BIGINT NOT NULL,
    status ENUM('LOADING','DEPARTED','ARRIVED') NOT NULL,
    vehicle_plate VARCHAR(20) NOT NULL,
    driver_name VARCHAR(100) NOT NULL,
    driver_phone VARCHAR(20) NOT NULL,
    seal_code VARCHAR(50) NULL,
    item_count INT NOT NULL DEFAULT 0,
    departed_by BIGINT NULL,
    departed_at DATETIME(6) NULL,
    arrived_by BIGINT NULL,
    arrived_at DATETIME(6) NULL,
    arrival_note VARCHAR(500) NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_linehaul_code UNIQUE (code),
    CONSTRAINT fk_trip_from FOREIGN KEY (from_branch_id) REFERENCES branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_trip_to FOREIGN KEY (to_branch_id) REFERENCES branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_trip_departed_by FOREIGN KEY (departed_by) REFERENCES app_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_trip_arrived_by FOREIGN KEY (arrived_by) REFERENCES app_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_trip_from_status (from_branch_id, status),
    INDEX idx_trip_to_status (to_branch_id, status),
    CONSTRAINT ck_trip_route_count CHECK (from_branch_id <> to_branch_id AND item_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS linehaul_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trip_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    received BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_linehaul_item UNIQUE (trip_id, order_id),
    CONSTRAINT fk_item_trip FOREIGN KEY (trip_id) REFERENCES linehaul_trip(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES shipment_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX idx_linehaul_item_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS route_tariff (
    id BIGINT NOT NULL AUTO_INCREMENT,
    origin_branch_id BIGINT NOT NULL,
    dest_branch_id BIGINT NOT NULL,
    service_type ENUM('STANDARD','EXPRESS','SUPER_EXPRESS') NOT NULL,
    surcharge DECIMAL(12,0) NOT NULL,
    lead_time_text VARCHAR(100) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_route_tariff UNIQUE (origin_branch_id, dest_branch_id, service_type),
    CONSTRAINT fk_tariff_origin FOREIGN KEY (origin_branch_id) REFERENCES branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tariff_dest FOREIGN KEY (dest_branch_id) REFERENCES branch(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_route_tariff CHECK (surcharge >= 0 AND (origin_branch_id = dest_branch_id OR service_type <> 'SUPER_EXPRESS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS saved_address_area (
    id BIGINT NOT NULL AUTO_INCREMENT,
    saved_address_id BIGINT NOT NULL,
    province_code VARCHAR(10) NOT NULL,
    area_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_saved_address_area UNIQUE (saved_address_id),
    CONSTRAINT fk_saved_area_address FOREIGN KEY (saved_address_id) REFERENCES saved_address(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_saved_area_area FOREIGN KEY (area_code) REFERENCES province_area(area_code) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS draft_order_area (
    id BIGINT NOT NULL AUTO_INCREMENT,
    draft_order_id BIGINT NOT NULL,
    pickup_province_code VARCHAR(10) NOT NULL,
    pickup_area_code VARCHAR(40) NOT NULL,
    delivery_province_code VARCHAR(10) NOT NULL,
    delivery_area_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_draft_order_area UNIQUE (draft_order_id),
    CONSTRAINT fk_draft_area_order FOREIGN KEY (draft_order_id) REFERENCES draft_order(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_draft_pickup_area FOREIGN KEY (pickup_area_code) REFERENCES province_area(area_code) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_draft_delivery_area FOREIGN KEY (delivery_area_code) REFERENCES province_area(area_code) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Chỉ thực thi ALTER dưới đây nếu cột chưa tồn tại; --apply tự kiểm tra điều kiện này.
ALTER TABLE operations_policy ADD COLUMN max_active_orders INT NOT NULL DEFAULT 15;

-- DDL tự commit. Chỉ phần INSERT danh mục được chạy trong giao dịch riêng.
START TRANSACTION;
INSERT INTO branch (code, name, province_code, hub_address, hub_area_code, active, revision, created_at, updated_at) SELECT 'SGN', 'Chi nhánh TP. Hồ Chí Minh', '79', 'Kho SGN, khu vực Thủ Đức, TP. Hồ Chí Minh', 'SGN-THUDUC', 1, 0, NOW(6), NOW(6) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM branch WHERE code = 'SGN');
INSERT INTO branch (code, name, province_code, hub_address, hub_area_code, active, revision, created_at, updated_at) SELECT 'HAN', 'Chi nhánh Hà Nội', '01', 'Kho HAN, khu vực Cầu Giấy, Hà Nội', 'HAN-CAUGIAY', 1, 0, NOW(6), NOW(6) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM branch WHERE code = 'HAN');
INSERT INTO branch (code, name, province_code, hub_address, hub_area_code, active, revision, created_at, updated_at) SELECT 'DAD', 'Chi nhánh Đà Nẵng', '48', 'Kho DAD, khu vực Hải Châu, Đà Nẵng', 'DAD-HAICHAU', 1, 0, NOW(6), NOW(6) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM branch WHERE code = 'DAD');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q1', 'Khu vực 1', b.id, 10.775600, 106.700400 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q1');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q3', 'Khu vực 3', b.id, 10.784000, 106.684000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q3');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q4', 'Khu vực 4', b.id, 10.757800, 106.704400 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q4');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q5', 'Khu vực 5', b.id, 10.754000, 106.666000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q5');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q6', 'Khu vực 6', b.id, 10.747700, 106.635000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q6');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q7', 'Khu vực 7', b.id, 10.734000, 106.722000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q7');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q8', 'Khu vực 8', b.id, 10.724000, 106.628000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q8');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q10', 'Khu vực 10', b.id, 10.773000, 106.667000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q10');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q11', 'Khu vực 11', b.id, 10.762000, 106.650000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q11');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-Q12', 'Khu vực 12', b.id, 10.867000, 106.654000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-Q12');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-BINHTAN', 'Bình Tân', b.id, 10.765000, 106.603000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-BINHTAN');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-BINHTHANH', 'Bình Thạnh', b.id, 10.811000, 106.710000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-BINHTHANH');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-GOVAP', 'Gò Vấp', b.id, 10.838000, 106.665000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-GOVAP');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-PHUNHUAN', 'Phú Nhuận', b.id, 10.800000, 106.680000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-PHUNHUAN');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-TANBINH', 'Tân Bình', b.id, 10.803000, 106.652000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-TANBINH');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-TANPHU', 'Tân Phú', b.id, 10.791000, 106.628000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-TANPHU');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-THUDUC', 'Thủ Đức', b.id, 10.849000, 106.753000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-THUDUC');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-BINHCHANH', 'Bình Chánh', b.id, 10.687000, 106.577000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-BINHCHANH');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-CUCHI', 'Củ Chi', b.id, 11.006000, 106.503000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-CUCHI');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-HOCMON', 'Hóc Môn', b.id, 10.889000, 106.595000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-HOCMON');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-NHABE', 'Nhà Bè', b.id, 10.696000, 106.740000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-NHABE');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '79', 'SGN-CANGIO', 'Cần Giờ', b.id, 10.411000, 106.954000 FROM branch b WHERE b.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'SGN-CANGIO');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-BADINH', 'Ba Đình', b.id, 21.036000, 105.819000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-BADINH');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-HOANKIEM', 'Hoàn Kiếm', b.id, 21.028000, 105.852000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-HOANKIEM');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-TAYHO', 'Tây Hồ', b.id, 21.071000, 105.819000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-TAYHO');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-LONGBIEN', 'Long Biên', b.id, 21.038000, 105.900000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-LONGBIEN');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-CAUGIAY', 'Cầu Giấy', b.id, 21.033000, 105.790000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-CAUGIAY');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-DONGDA', 'Đống Đa', b.id, 21.014000, 105.829000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-DONGDA');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-HAIBATRUNG', 'Hai Bà Trưng', b.id, 21.005000, 105.858000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-HAIBATRUNG');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-HOANGMAI', 'Hoàng Mai', b.id, 20.975000, 105.863000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-HOANGMAI');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-THANHXUAN', 'Thanh Xuân', b.id, 20.994000, 105.811000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-THANHXUAN');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-HADONG', 'Hà Đông', b.id, 20.971000, 105.778000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-HADONG');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-BACTULIEM', 'Bắc Từ Liêm', b.id, 21.071000, 105.757000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-BACTULIEM');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-NAMTULIEM', 'Nam Từ Liêm', b.id, 21.016000, 105.765000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-NAMTULIEM');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-SONTAY', 'Sơn Tây', b.id, 21.140000, 105.505000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-SONTAY');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-BAVI', 'Ba Vì', b.id, 21.199000, 105.421000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-BAVI');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-CHUONGMY', 'Chương Mỹ', b.id, 20.923000, 105.704000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-CHUONGMY');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-DANPHUONG', 'Đan Phượng', b.id, 21.089000, 105.669000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-DANPHUONG');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-DONGANH', 'Đông Anh', b.id, 21.139000, 105.848000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-DONGANH');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-GIALAM', 'Gia Lâm', b.id, 21.019000, 105.941000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-GIALAM');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-HOAIDUC', 'Hoài Đức', b.id, 21.038000, 105.707000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-HOAIDUC');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-MELINH', 'Mê Linh', b.id, 21.183000, 105.720000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-MELINH');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-MYDUC', 'Mỹ Đức', b.id, 20.686000, 105.746000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-MYDUC');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-PHUXUYEN', 'Phú Xuyên', b.id, 20.746000, 105.910000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-PHUXUYEN');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-PHUCTHO', 'Phúc Thọ', b.id, 21.104000, 105.581000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-PHUCTHO');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-QUOCOAI', 'Quốc Oai', b.id, 20.991000, 105.642000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-QUOCOAI');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-SOCSON', 'Sóc Sơn', b.id, 21.257000, 105.848000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-SOCSON');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-THACHTHAT', 'Thạch Thất', b.id, 21.057000, 105.556000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-THACHTHAT');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-THANHOAI', 'Thanh Oai', b.id, 20.857000, 105.769000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-THANHOAI');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-THANHTRI', 'Thanh Trì', b.id, 20.945000, 105.850000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-THANHTRI');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-THUONGTIN', 'Thường Tín', b.id, 20.868000, 105.862000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-THUONGTIN');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '01', 'HAN-UNGHOA', 'Ứng Hòa', b.id, 20.727000, 105.770000 FROM branch b WHERE b.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'HAN-UNGHOA');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '48', 'DAD-HAICHAU', 'Hải Châu', b.id, 16.047000, 108.218000 FROM branch b WHERE b.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'DAD-HAICHAU');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '48', 'DAD-THANHKHE', 'Thanh Khê', b.id, 16.064000, 108.184000 FROM branch b WHERE b.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'DAD-THANHKHE');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '48', 'DAD-SONTRA', 'Sơn Trà', b.id, 16.091000, 108.265000 FROM branch b WHERE b.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'DAD-SONTRA');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '48', 'DAD-NGUHANHSON', 'Ngũ Hành Sơn', b.id, 16.004000, 108.252000 FROM branch b WHERE b.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'DAD-NGUHANHSON');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '48', 'DAD-LIENCHIEU', 'Liên Chiểu', b.id, 16.079000, 108.149000 FROM branch b WHERE b.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'DAD-LIENCHIEU');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '48', 'DAD-CAMLE', 'Cẩm Lệ', b.id, 16.016000, 108.199000 FROM branch b WHERE b.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'DAD-CAMLE');
INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT '48', 'DAD-HOAVANG', 'Hòa Vang', b.id, 16.005000, 108.106000 FROM branch b WHERE b.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = 'DAD-HOAVANG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-CAMLE', 'DAD-HAICHAU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-CAMLE' AND neighbor_code = 'DAD-HAICHAU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-HAICHAU', 'DAD-CAMLE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-HAICHAU' AND neighbor_code = 'DAD-CAMLE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-CAMLE', 'DAD-HOAVANG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-CAMLE' AND neighbor_code = 'DAD-HOAVANG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-HOAVANG', 'DAD-CAMLE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-HOAVANG' AND neighbor_code = 'DAD-CAMLE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-CAMLE', 'DAD-LIENCHIEU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-CAMLE' AND neighbor_code = 'DAD-LIENCHIEU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-LIENCHIEU', 'DAD-CAMLE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-LIENCHIEU' AND neighbor_code = 'DAD-CAMLE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-CAMLE', 'DAD-NGUHANHSON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-CAMLE' AND neighbor_code = 'DAD-NGUHANHSON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-NGUHANHSON', 'DAD-CAMLE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-NGUHANHSON' AND neighbor_code = 'DAD-CAMLE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-CAMLE', 'DAD-THANHKHE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-CAMLE' AND neighbor_code = 'DAD-THANHKHE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-THANHKHE', 'DAD-CAMLE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-THANHKHE' AND neighbor_code = 'DAD-CAMLE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-HAICHAU', 'DAD-NGUHANHSON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-HAICHAU' AND neighbor_code = 'DAD-NGUHANHSON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-NGUHANHSON', 'DAD-HAICHAU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-NGUHANHSON' AND neighbor_code = 'DAD-HAICHAU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-HAICHAU', 'DAD-SONTRA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-HAICHAU' AND neighbor_code = 'DAD-SONTRA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-SONTRA', 'DAD-HAICHAU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-SONTRA' AND neighbor_code = 'DAD-HAICHAU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-HAICHAU', 'DAD-THANHKHE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-HAICHAU' AND neighbor_code = 'DAD-THANHKHE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-THANHKHE', 'DAD-HAICHAU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-THANHKHE' AND neighbor_code = 'DAD-HAICHAU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-HOAVANG', 'DAD-LIENCHIEU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-HOAVANG' AND neighbor_code = 'DAD-LIENCHIEU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-LIENCHIEU', 'DAD-HOAVANG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-LIENCHIEU' AND neighbor_code = 'DAD-HOAVANG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-HOAVANG', 'DAD-NGUHANHSON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-HOAVANG' AND neighbor_code = 'DAD-NGUHANHSON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-NGUHANHSON', 'DAD-HOAVANG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-NGUHANHSON' AND neighbor_code = 'DAD-HOAVANG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-LIENCHIEU', 'DAD-THANHKHE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-LIENCHIEU' AND neighbor_code = 'DAD-THANHKHE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-THANHKHE', 'DAD-LIENCHIEU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-THANHKHE' AND neighbor_code = 'DAD-LIENCHIEU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-NGUHANHSON', 'DAD-SONTRA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-NGUHANHSON' AND neighbor_code = 'DAD-SONTRA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'DAD-SONTRA', 'DAD-NGUHANHSON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'DAD-SONTRA' AND neighbor_code = 'DAD-NGUHANHSON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BACTULIEM', 'HAN-CAUGIAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BACTULIEM' AND neighbor_code = 'HAN-CAUGIAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CAUGIAY', 'HAN-BACTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CAUGIAY' AND neighbor_code = 'HAN-BACTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BACTULIEM', 'HAN-DANPHUONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BACTULIEM' AND neighbor_code = 'HAN-DANPHUONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DANPHUONG', 'HAN-BACTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DANPHUONG' AND neighbor_code = 'HAN-BACTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BACTULIEM', 'HAN-DONGANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BACTULIEM' AND neighbor_code = 'HAN-DONGANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGANH', 'HAN-BACTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGANH' AND neighbor_code = 'HAN-BACTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BACTULIEM', 'HAN-HOAIDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BACTULIEM' AND neighbor_code = 'HAN-HOAIDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOAIDUC', 'HAN-BACTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOAIDUC' AND neighbor_code = 'HAN-BACTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BACTULIEM', 'HAN-MELINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BACTULIEM' AND neighbor_code = 'HAN-MELINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-MELINH', 'HAN-BACTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-MELINH' AND neighbor_code = 'HAN-BACTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BACTULIEM', 'HAN-NAMTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BACTULIEM' AND neighbor_code = 'HAN-NAMTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-NAMTULIEM', 'HAN-BACTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-NAMTULIEM' AND neighbor_code = 'HAN-BACTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BACTULIEM', 'HAN-TAYHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BACTULIEM' AND neighbor_code = 'HAN-TAYHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-TAYHO', 'HAN-BACTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-TAYHO' AND neighbor_code = 'HAN-BACTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BADINH', 'HAN-CAUGIAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BADINH' AND neighbor_code = 'HAN-CAUGIAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CAUGIAY', 'HAN-BADINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CAUGIAY' AND neighbor_code = 'HAN-BADINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BADINH', 'HAN-DONGDA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BADINH' AND neighbor_code = 'HAN-DONGDA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGDA', 'HAN-BADINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGDA' AND neighbor_code = 'HAN-BADINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BADINH', 'HAN-HOANKIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BADINH' AND neighbor_code = 'HAN-HOANKIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANKIEM', 'HAN-BADINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANKIEM' AND neighbor_code = 'HAN-BADINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BADINH', 'HAN-LONGBIEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BADINH' AND neighbor_code = 'HAN-LONGBIEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-LONGBIEN', 'HAN-BADINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-LONGBIEN' AND neighbor_code = 'HAN-BADINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BADINH', 'HAN-TAYHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BADINH' AND neighbor_code = 'HAN-TAYHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-TAYHO', 'HAN-BADINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-TAYHO' AND neighbor_code = 'HAN-BADINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BAVI', 'HAN-PHUCTHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BAVI' AND neighbor_code = 'HAN-PHUCTHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUCTHO', 'HAN-BAVI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUCTHO' AND neighbor_code = 'HAN-BAVI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-BAVI', 'HAN-SONTAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-BAVI' AND neighbor_code = 'HAN-SONTAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-SONTAY', 'HAN-BAVI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-SONTAY' AND neighbor_code = 'HAN-BAVI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CAUGIAY', 'HAN-DONGDA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CAUGIAY' AND neighbor_code = 'HAN-DONGDA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGDA', 'HAN-CAUGIAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGDA' AND neighbor_code = 'HAN-CAUGIAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CAUGIAY', 'HAN-NAMTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CAUGIAY' AND neighbor_code = 'HAN-NAMTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-NAMTULIEM', 'HAN-CAUGIAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-NAMTULIEM' AND neighbor_code = 'HAN-CAUGIAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CAUGIAY', 'HAN-TAYHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CAUGIAY' AND neighbor_code = 'HAN-TAYHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-TAYHO', 'HAN-CAUGIAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-TAYHO' AND neighbor_code = 'HAN-CAUGIAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CAUGIAY', 'HAN-THANHXUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CAUGIAY' AND neighbor_code = 'HAN-THANHXUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHXUAN', 'HAN-CAUGIAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHXUAN' AND neighbor_code = 'HAN-CAUGIAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CHUONGMY', 'HAN-HADONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CHUONGMY' AND neighbor_code = 'HAN-HADONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HADONG', 'HAN-CHUONGMY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HADONG' AND neighbor_code = 'HAN-CHUONGMY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CHUONGMY', 'HAN-MYDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CHUONGMY' AND neighbor_code = 'HAN-MYDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-MYDUC', 'HAN-CHUONGMY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-MYDUC' AND neighbor_code = 'HAN-CHUONGMY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CHUONGMY', 'HAN-QUOCOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CHUONGMY' AND neighbor_code = 'HAN-QUOCOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-QUOCOAI', 'HAN-CHUONGMY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-QUOCOAI' AND neighbor_code = 'HAN-CHUONGMY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-CHUONGMY', 'HAN-THANHOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-CHUONGMY' AND neighbor_code = 'HAN-THANHOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHOAI', 'HAN-CHUONGMY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHOAI' AND neighbor_code = 'HAN-CHUONGMY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DANPHUONG', 'HAN-HOAIDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DANPHUONG' AND neighbor_code = 'HAN-HOAIDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOAIDUC', 'HAN-DANPHUONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOAIDUC' AND neighbor_code = 'HAN-DANPHUONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DANPHUONG', 'HAN-MELINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DANPHUONG' AND neighbor_code = 'HAN-MELINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-MELINH', 'HAN-DANPHUONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-MELINH' AND neighbor_code = 'HAN-DANPHUONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DANPHUONG', 'HAN-PHUCTHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DANPHUONG' AND neighbor_code = 'HAN-PHUCTHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUCTHO', 'HAN-DANPHUONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUCTHO' AND neighbor_code = 'HAN-DANPHUONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGANH', 'HAN-GIALAM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGANH' AND neighbor_code = 'HAN-GIALAM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-GIALAM', 'HAN-DONGANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-GIALAM' AND neighbor_code = 'HAN-DONGANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGANH', 'HAN-LONGBIEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGANH' AND neighbor_code = 'HAN-LONGBIEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-LONGBIEN', 'HAN-DONGANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-LONGBIEN' AND neighbor_code = 'HAN-DONGANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGANH', 'HAN-MELINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGANH' AND neighbor_code = 'HAN-MELINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-MELINH', 'HAN-DONGANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-MELINH' AND neighbor_code = 'HAN-DONGANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGANH', 'HAN-SOCSON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGANH' AND neighbor_code = 'HAN-SOCSON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-SOCSON', 'HAN-DONGANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-SOCSON' AND neighbor_code = 'HAN-DONGANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGANH', 'HAN-TAYHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGANH' AND neighbor_code = 'HAN-TAYHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-TAYHO', 'HAN-DONGANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-TAYHO' AND neighbor_code = 'HAN-DONGANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGDA', 'HAN-HAIBATRUNG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGDA' AND neighbor_code = 'HAN-HAIBATRUNG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HAIBATRUNG', 'HAN-DONGDA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HAIBATRUNG' AND neighbor_code = 'HAN-DONGDA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGDA', 'HAN-HOANKIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGDA' AND neighbor_code = 'HAN-HOANKIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANKIEM', 'HAN-DONGDA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANKIEM' AND neighbor_code = 'HAN-DONGDA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-DONGDA', 'HAN-THANHXUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-DONGDA' AND neighbor_code = 'HAN-THANHXUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHXUAN', 'HAN-DONGDA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHXUAN' AND neighbor_code = 'HAN-DONGDA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-GIALAM', 'HAN-HOANGMAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-GIALAM' AND neighbor_code = 'HAN-HOANGMAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANGMAI', 'HAN-GIALAM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANGMAI' AND neighbor_code = 'HAN-GIALAM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-GIALAM', 'HAN-LONGBIEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-GIALAM' AND neighbor_code = 'HAN-LONGBIEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-LONGBIEN', 'HAN-GIALAM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-LONGBIEN' AND neighbor_code = 'HAN-GIALAM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-GIALAM', 'HAN-THANHTRI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-GIALAM' AND neighbor_code = 'HAN-THANHTRI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHTRI', 'HAN-GIALAM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHTRI' AND neighbor_code = 'HAN-GIALAM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HADONG', 'HAN-HOAIDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HADONG' AND neighbor_code = 'HAN-HOAIDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOAIDUC', 'HAN-HADONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOAIDUC' AND neighbor_code = 'HAN-HADONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HADONG', 'HAN-NAMTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HADONG' AND neighbor_code = 'HAN-NAMTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-NAMTULIEM', 'HAN-HADONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-NAMTULIEM' AND neighbor_code = 'HAN-HADONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HADONG', 'HAN-QUOCOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HADONG' AND neighbor_code = 'HAN-QUOCOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-QUOCOAI', 'HAN-HADONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-QUOCOAI' AND neighbor_code = 'HAN-HADONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HADONG', 'HAN-THANHOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HADONG' AND neighbor_code = 'HAN-THANHOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHOAI', 'HAN-HADONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHOAI' AND neighbor_code = 'HAN-HADONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HADONG', 'HAN-THANHTRI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HADONG' AND neighbor_code = 'HAN-THANHTRI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHTRI', 'HAN-HADONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHTRI' AND neighbor_code = 'HAN-HADONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HADONG', 'HAN-THANHXUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HADONG' AND neighbor_code = 'HAN-THANHXUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHXUAN', 'HAN-HADONG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHXUAN' AND neighbor_code = 'HAN-HADONG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HAIBATRUNG', 'HAN-HOANGMAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HAIBATRUNG' AND neighbor_code = 'HAN-HOANGMAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANGMAI', 'HAN-HAIBATRUNG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANGMAI' AND neighbor_code = 'HAN-HAIBATRUNG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HAIBATRUNG', 'HAN-HOANKIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HAIBATRUNG' AND neighbor_code = 'HAN-HOANKIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANKIEM', 'HAN-HAIBATRUNG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANKIEM' AND neighbor_code = 'HAN-HAIBATRUNG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HAIBATRUNG', 'HAN-LONGBIEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HAIBATRUNG' AND neighbor_code = 'HAN-LONGBIEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-LONGBIEN', 'HAN-HAIBATRUNG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-LONGBIEN' AND neighbor_code = 'HAN-HAIBATRUNG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HAIBATRUNG', 'HAN-THANHXUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HAIBATRUNG' AND neighbor_code = 'HAN-THANHXUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHXUAN', 'HAN-HAIBATRUNG' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHXUAN' AND neighbor_code = 'HAN-HAIBATRUNG');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOAIDUC', 'HAN-NAMTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOAIDUC' AND neighbor_code = 'HAN-NAMTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-NAMTULIEM', 'HAN-HOAIDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-NAMTULIEM' AND neighbor_code = 'HAN-HOAIDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOAIDUC', 'HAN-PHUCTHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOAIDUC' AND neighbor_code = 'HAN-PHUCTHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUCTHO', 'HAN-HOAIDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUCTHO' AND neighbor_code = 'HAN-HOAIDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOAIDUC', 'HAN-QUOCOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOAIDUC' AND neighbor_code = 'HAN-QUOCOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-QUOCOAI', 'HAN-HOAIDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-QUOCOAI' AND neighbor_code = 'HAN-HOAIDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANGMAI', 'HAN-LONGBIEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANGMAI' AND neighbor_code = 'HAN-LONGBIEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-LONGBIEN', 'HAN-HOANGMAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-LONGBIEN' AND neighbor_code = 'HAN-HOANGMAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANGMAI', 'HAN-THANHTRI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANGMAI' AND neighbor_code = 'HAN-THANHTRI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHTRI', 'HAN-HOANGMAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHTRI' AND neighbor_code = 'HAN-HOANGMAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANGMAI', 'HAN-THANHXUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANGMAI' AND neighbor_code = 'HAN-THANHXUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHXUAN', 'HAN-HOANGMAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHXUAN' AND neighbor_code = 'HAN-HOANGMAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-HOANKIEM', 'HAN-LONGBIEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-HOANKIEM' AND neighbor_code = 'HAN-LONGBIEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-LONGBIEN', 'HAN-HOANKIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-LONGBIEN' AND neighbor_code = 'HAN-HOANKIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-LONGBIEN', 'HAN-TAYHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-LONGBIEN' AND neighbor_code = 'HAN-TAYHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-TAYHO', 'HAN-LONGBIEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-TAYHO' AND neighbor_code = 'HAN-LONGBIEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-MELINH', 'HAN-PHUCTHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-MELINH' AND neighbor_code = 'HAN-PHUCTHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUCTHO', 'HAN-MELINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUCTHO' AND neighbor_code = 'HAN-MELINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-MELINH', 'HAN-SOCSON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-MELINH' AND neighbor_code = 'HAN-SOCSON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-SOCSON', 'HAN-MELINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-SOCSON' AND neighbor_code = 'HAN-MELINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-MYDUC', 'HAN-UNGHOA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-MYDUC' AND neighbor_code = 'HAN-UNGHOA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-UNGHOA', 'HAN-MYDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-UNGHOA' AND neighbor_code = 'HAN-MYDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-NAMTULIEM', 'HAN-THANHXUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-NAMTULIEM' AND neighbor_code = 'HAN-THANHXUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHXUAN', 'HAN-NAMTULIEM' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHXUAN' AND neighbor_code = 'HAN-NAMTULIEM');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUCTHO', 'HAN-QUOCOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUCTHO' AND neighbor_code = 'HAN-QUOCOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-QUOCOAI', 'HAN-PHUCTHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-QUOCOAI' AND neighbor_code = 'HAN-PHUCTHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUCTHO', 'HAN-SONTAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUCTHO' AND neighbor_code = 'HAN-SONTAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-SONTAY', 'HAN-PHUCTHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-SONTAY' AND neighbor_code = 'HAN-PHUCTHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUCTHO', 'HAN-THACHTHAT' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUCTHO' AND neighbor_code = 'HAN-THACHTHAT');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THACHTHAT', 'HAN-PHUCTHO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THACHTHAT' AND neighbor_code = 'HAN-PHUCTHO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUXUYEN', 'HAN-THUONGTIN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUXUYEN' AND neighbor_code = 'HAN-THUONGTIN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THUONGTIN', 'HAN-PHUXUYEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THUONGTIN' AND neighbor_code = 'HAN-PHUXUYEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-PHUXUYEN', 'HAN-UNGHOA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-PHUXUYEN' AND neighbor_code = 'HAN-UNGHOA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-UNGHOA', 'HAN-PHUXUYEN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-UNGHOA' AND neighbor_code = 'HAN-PHUXUYEN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-QUOCOAI', 'HAN-THACHTHAT' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-QUOCOAI' AND neighbor_code = 'HAN-THACHTHAT');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THACHTHAT', 'HAN-QUOCOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THACHTHAT' AND neighbor_code = 'HAN-QUOCOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-SONTAY', 'HAN-THACHTHAT' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-SONTAY' AND neighbor_code = 'HAN-THACHTHAT');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THACHTHAT', 'HAN-SONTAY' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THACHTHAT' AND neighbor_code = 'HAN-SONTAY');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHOAI', 'HAN-THANHTRI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHOAI' AND neighbor_code = 'HAN-THANHTRI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHTRI', 'HAN-THANHOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHTRI' AND neighbor_code = 'HAN-THANHOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHOAI', 'HAN-THUONGTIN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHOAI' AND neighbor_code = 'HAN-THUONGTIN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THUONGTIN', 'HAN-THANHOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THUONGTIN' AND neighbor_code = 'HAN-THANHOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHOAI', 'HAN-UNGHOA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHOAI' AND neighbor_code = 'HAN-UNGHOA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-UNGHOA', 'HAN-THANHOAI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-UNGHOA' AND neighbor_code = 'HAN-THANHOAI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHTRI', 'HAN-THANHXUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHTRI' AND neighbor_code = 'HAN-THANHXUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHXUAN', 'HAN-THANHTRI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHXUAN' AND neighbor_code = 'HAN-THANHTRI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THANHTRI', 'HAN-THUONGTIN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THANHTRI' AND neighbor_code = 'HAN-THUONGTIN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THUONGTIN', 'HAN-THANHTRI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THUONGTIN' AND neighbor_code = 'HAN-THANHTRI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-THUONGTIN', 'HAN-UNGHOA' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-THUONGTIN' AND neighbor_code = 'HAN-UNGHOA');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'HAN-UNGHOA', 'HAN-THUONGTIN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'HAN-UNGHOA' AND neighbor_code = 'HAN-THUONGTIN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHCHANH', 'SGN-BINHTAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHCHANH' AND neighbor_code = 'SGN-BINHTAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTAN', 'SGN-BINHCHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTAN' AND neighbor_code = 'SGN-BINHCHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHCHANH', 'SGN-HOCMON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHCHANH' AND neighbor_code = 'SGN-HOCMON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-HOCMON', 'SGN-BINHCHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-HOCMON' AND neighbor_code = 'SGN-BINHCHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHCHANH', 'SGN-NHABE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHCHANH' AND neighbor_code = 'SGN-NHABE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-NHABE', 'SGN-BINHCHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-NHABE' AND neighbor_code = 'SGN-BINHCHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHCHANH', 'SGN-Q7' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHCHANH' AND neighbor_code = 'SGN-Q7');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q7', 'SGN-BINHCHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q7' AND neighbor_code = 'SGN-BINHCHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHCHANH', 'SGN-Q8' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHCHANH' AND neighbor_code = 'SGN-Q8');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q8', 'SGN-BINHCHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q8' AND neighbor_code = 'SGN-BINHCHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTAN', 'SGN-HOCMON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTAN' AND neighbor_code = 'SGN-HOCMON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-HOCMON', 'SGN-BINHTAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-HOCMON' AND neighbor_code = 'SGN-BINHTAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTAN', 'SGN-Q12' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTAN' AND neighbor_code = 'SGN-Q12');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q12', 'SGN-BINHTAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q12' AND neighbor_code = 'SGN-BINHTAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTAN', 'SGN-Q6' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTAN' AND neighbor_code = 'SGN-Q6');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q6', 'SGN-BINHTAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q6' AND neighbor_code = 'SGN-BINHTAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTAN', 'SGN-Q8' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTAN' AND neighbor_code = 'SGN-Q8');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q8', 'SGN-BINHTAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q8' AND neighbor_code = 'SGN-BINHTAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTAN', 'SGN-TANPHU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTAN' AND neighbor_code = 'SGN-TANPHU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANPHU', 'SGN-BINHTAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANPHU' AND neighbor_code = 'SGN-BINHTAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTHANH', 'SGN-GOVAP' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTHANH' AND neighbor_code = 'SGN-GOVAP');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-GOVAP', 'SGN-BINHTHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-GOVAP' AND neighbor_code = 'SGN-BINHTHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTHANH', 'SGN-PHUNHUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTHANH' AND neighbor_code = 'SGN-PHUNHUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-PHUNHUAN', 'SGN-BINHTHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-PHUNHUAN' AND neighbor_code = 'SGN-BINHTHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTHANH', 'SGN-Q1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTHANH' AND neighbor_code = 'SGN-Q1');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q1', 'SGN-BINHTHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q1' AND neighbor_code = 'SGN-BINHTHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTHANH', 'SGN-Q12' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTHANH' AND neighbor_code = 'SGN-Q12');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q12', 'SGN-BINHTHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q12' AND neighbor_code = 'SGN-BINHTHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-BINHTHANH', 'SGN-THUDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-BINHTHANH' AND neighbor_code = 'SGN-THUDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-THUDUC', 'SGN-BINHTHANH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-THUDUC' AND neighbor_code = 'SGN-BINHTHANH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-CANGIO', 'SGN-NHABE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-CANGIO' AND neighbor_code = 'SGN-NHABE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-NHABE', 'SGN-CANGIO' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-NHABE' AND neighbor_code = 'SGN-CANGIO');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-CUCHI', 'SGN-HOCMON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-CUCHI' AND neighbor_code = 'SGN-HOCMON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-HOCMON', 'SGN-CUCHI' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-HOCMON' AND neighbor_code = 'SGN-CUCHI');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-GOVAP', 'SGN-PHUNHUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-GOVAP' AND neighbor_code = 'SGN-PHUNHUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-PHUNHUAN', 'SGN-GOVAP' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-PHUNHUAN' AND neighbor_code = 'SGN-GOVAP');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-GOVAP', 'SGN-Q12' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-GOVAP' AND neighbor_code = 'SGN-Q12');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q12', 'SGN-GOVAP' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q12' AND neighbor_code = 'SGN-GOVAP');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-GOVAP', 'SGN-TANBINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-GOVAP' AND neighbor_code = 'SGN-TANBINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANBINH', 'SGN-GOVAP' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANBINH' AND neighbor_code = 'SGN-GOVAP');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-HOCMON', 'SGN-Q12' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-HOCMON' AND neighbor_code = 'SGN-Q12');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q12', 'SGN-HOCMON' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q12' AND neighbor_code = 'SGN-HOCMON');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-NHABE', 'SGN-Q7' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-NHABE' AND neighbor_code = 'SGN-Q7');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q7', 'SGN-NHABE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q7' AND neighbor_code = 'SGN-NHABE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-NHABE', 'SGN-THUDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-NHABE' AND neighbor_code = 'SGN-THUDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-THUDUC', 'SGN-NHABE' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-THUDUC' AND neighbor_code = 'SGN-NHABE');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-PHUNHUAN', 'SGN-Q1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-PHUNHUAN' AND neighbor_code = 'SGN-Q1');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q1', 'SGN-PHUNHUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q1' AND neighbor_code = 'SGN-PHUNHUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-PHUNHUAN', 'SGN-Q3' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-PHUNHUAN' AND neighbor_code = 'SGN-Q3');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q3', 'SGN-PHUNHUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q3' AND neighbor_code = 'SGN-PHUNHUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-PHUNHUAN', 'SGN-TANBINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-PHUNHUAN' AND neighbor_code = 'SGN-TANBINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANBINH', 'SGN-PHUNHUAN' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANBINH' AND neighbor_code = 'SGN-PHUNHUAN');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q1', 'SGN-Q10' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q1' AND neighbor_code = 'SGN-Q10');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q10', 'SGN-Q1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q10' AND neighbor_code = 'SGN-Q1');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q1', 'SGN-Q3' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q1' AND neighbor_code = 'SGN-Q3');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q3', 'SGN-Q1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q3' AND neighbor_code = 'SGN-Q1');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q1', 'SGN-Q4' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q1' AND neighbor_code = 'SGN-Q4');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q4', 'SGN-Q1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q4' AND neighbor_code = 'SGN-Q1');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q1', 'SGN-Q5' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q1' AND neighbor_code = 'SGN-Q5');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q5', 'SGN-Q1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q5' AND neighbor_code = 'SGN-Q1');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q1', 'SGN-THUDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q1' AND neighbor_code = 'SGN-THUDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-THUDUC', 'SGN-Q1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-THUDUC' AND neighbor_code = 'SGN-Q1');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q10', 'SGN-Q11' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q10' AND neighbor_code = 'SGN-Q11');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q11', 'SGN-Q10' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q11' AND neighbor_code = 'SGN-Q10');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q10', 'SGN-Q3' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q10' AND neighbor_code = 'SGN-Q3');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q3', 'SGN-Q10' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q3' AND neighbor_code = 'SGN-Q10');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q10', 'SGN-Q5' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q10' AND neighbor_code = 'SGN-Q5');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q5', 'SGN-Q10' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q5' AND neighbor_code = 'SGN-Q10');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q10', 'SGN-TANBINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q10' AND neighbor_code = 'SGN-TANBINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANBINH', 'SGN-Q10' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANBINH' AND neighbor_code = 'SGN-Q10');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q11', 'SGN-Q5' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q11' AND neighbor_code = 'SGN-Q5');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q5', 'SGN-Q11' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q5' AND neighbor_code = 'SGN-Q11');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q11', 'SGN-Q6' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q11' AND neighbor_code = 'SGN-Q6');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q6', 'SGN-Q11' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q6' AND neighbor_code = 'SGN-Q11');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q11', 'SGN-TANBINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q11' AND neighbor_code = 'SGN-TANBINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANBINH', 'SGN-Q11' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANBINH' AND neighbor_code = 'SGN-Q11');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q11', 'SGN-TANPHU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q11' AND neighbor_code = 'SGN-TANPHU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANPHU', 'SGN-Q11' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANPHU' AND neighbor_code = 'SGN-Q11');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q12', 'SGN-TANBINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q12' AND neighbor_code = 'SGN-TANBINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANBINH', 'SGN-Q12' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANBINH' AND neighbor_code = 'SGN-Q12');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q12', 'SGN-TANPHU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q12' AND neighbor_code = 'SGN-TANPHU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANPHU', 'SGN-Q12' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANPHU' AND neighbor_code = 'SGN-Q12');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q12', 'SGN-THUDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q12' AND neighbor_code = 'SGN-THUDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-THUDUC', 'SGN-Q12' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-THUDUC' AND neighbor_code = 'SGN-Q12');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q3', 'SGN-TANBINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q3' AND neighbor_code = 'SGN-TANBINH');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANBINH', 'SGN-Q3' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANBINH' AND neighbor_code = 'SGN-Q3');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q4', 'SGN-Q7' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q4' AND neighbor_code = 'SGN-Q7');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q7', 'SGN-Q4' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q7' AND neighbor_code = 'SGN-Q4');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q4', 'SGN-Q8' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q4' AND neighbor_code = 'SGN-Q8');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q8', 'SGN-Q4' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q8' AND neighbor_code = 'SGN-Q4');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q5', 'SGN-Q6' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q5' AND neighbor_code = 'SGN-Q6');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q6', 'SGN-Q5' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q6' AND neighbor_code = 'SGN-Q5');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q5', 'SGN-Q8' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q5' AND neighbor_code = 'SGN-Q8');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q8', 'SGN-Q5' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q8' AND neighbor_code = 'SGN-Q5');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q6', 'SGN-Q8' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q6' AND neighbor_code = 'SGN-Q8');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q8', 'SGN-Q6' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q8' AND neighbor_code = 'SGN-Q6');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q6', 'SGN-TANPHU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q6' AND neighbor_code = 'SGN-TANPHU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANPHU', 'SGN-Q6' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANPHU' AND neighbor_code = 'SGN-Q6');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q7', 'SGN-Q8' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q7' AND neighbor_code = 'SGN-Q8');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q8', 'SGN-Q7' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q8' AND neighbor_code = 'SGN-Q7');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-Q7', 'SGN-THUDUC' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-Q7' AND neighbor_code = 'SGN-THUDUC');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-THUDUC', 'SGN-Q7' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-THUDUC' AND neighbor_code = 'SGN-Q7');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANBINH', 'SGN-TANPHU' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANBINH' AND neighbor_code = 'SGN-TANPHU');
INSERT INTO area_neighbor (area_code, neighbor_code) SELECT 'SGN-TANPHU', 'SGN-TANBINH' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = 'SGN-TANPHU' AND neighbor_code = 'SGN-TANBINH');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 0, '1–2 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'SGN' AND d.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 0, 'Trong ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'SGN' AND d.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'SUPER_EXPRESS', 0, 'Trong 4 giờ', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'SGN' AND d.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'SUPER_EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 25000, '4–5 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'SGN' AND d.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 40000, '2–3 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'SGN' AND d.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 15000, '3–4 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'SGN' AND d.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 25000, '2 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'SGN' AND d.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 25000, '4–5 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'HAN' AND d.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 40000, '2–3 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'HAN' AND d.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 0, '1–2 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'HAN' AND d.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 0, 'Trong ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'HAN' AND d.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'SUPER_EXPRESS', 0, 'Trong 4 giờ', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'HAN' AND d.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'SUPER_EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 15000, '3–4 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'HAN' AND d.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 25000, '2 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'HAN' AND d.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 15000, '3–4 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'DAD' AND d.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 25000, '2 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'DAD' AND d.code = 'SGN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 15000, '3–4 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'DAD' AND d.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 25000, '2 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'DAD' AND d.code = 'HAN' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'STANDARD', 0, '1–2 ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'DAD' AND d.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'STANDARD');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'EXPRESS', 0, 'Trong ngày', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'DAD' AND d.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'EXPRESS');
INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, 'SUPER_EXPRESS', 0, 'Trong 4 giờ', 0 FROM branch b CROSS JOIN branch d WHERE b.code = 'DAD' AND d.code = 'DAD' AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = 'SUPER_EXPRESS');
COMMIT;
