-- Thông tin cá nhân mở rộng của người gửi / Shipper: ảnh đại diện (mã tệp Cloudinary), ngày sinh,
-- giới tính, giới thiệu, địa chỉ liên hệ, tùy chọn email cập nhật vận đơn.
-- Chỉ thêm một bảng mới; không sửa bảng app_user hay dữ liệu có sẵn.
CREATE TABLE IF NOT EXISTS user_profile_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    avatar_asset VARCHAR(255),
    birth_date DATE,
    gender ENUM('FEMALE','MALE','OTHER'),
    bio VARCHAR(160) CHARACTER SET utf8mb4,
    province VARCHAR(60) CHARACTER SET utf8mb4,
    address_line VARCHAR(200) CHARACTER SET utf8mb4,
    notify_order_email BIT NOT NULL DEFAULT b'1',
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_profile_detail_user UNIQUE (user_id),
    CONSTRAINT fk_user_profile_detail_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
