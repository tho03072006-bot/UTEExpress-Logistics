-- Bảng bổ sung; không đổi dữ liệu hoặc cấu trúc bảng nghiệp vụ cũ.
-- Dùng UpgradeAivenOrderSubmissions --check / --apply để kiểm tra trước và chạy lại an toàn.
CREATE TABLE order_form_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(10) NOT NULL,
    resource_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_form_token UNIQUE (token),
    CONSTRAINT fk_order_form_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
