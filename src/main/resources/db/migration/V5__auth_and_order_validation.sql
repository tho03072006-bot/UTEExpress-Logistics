-- Tham khảo schema. Để chạy lại an toàn hãy dùng UpgradeAivenAuthValidation --check / --apply.
-- Chỉ bổ sung cột và UNIQUE; đơn cũ giữ creation_token/hash NULL, không đổi dữ liệu nghiệp vụ.
ALTER TABLE app_user ADD COLUMN otp_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN otp_last_sent_at DATETIME(6) NULL;
ALTER TABLE app_user ADD COLUMN otp_window_start DATETIME(6) NULL;
ALTER TABLE app_user ADD COLUMN otp_send_count INT NOT NULL DEFAULT 0;
ALTER TABLE shipment_order ADD COLUMN creation_token VARCHAR(36) NULL;
ALTER TABLE shipment_order ADD COLUMN creation_hash VARCHAR(64) NULL;
ALTER TABLE shipment_order ADD CONSTRAINT uk_shipment_creation_token UNIQUE (creation_token);
