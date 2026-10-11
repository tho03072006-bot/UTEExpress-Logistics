-- Đối soát COD: chỉ thêm hai bảng mới; không sửa dữ liệu hay cột của bảng có sẵn.
CREATE TABLE IF NOT EXISTS cod_settlement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision BIGINT NOT NULL DEFAULT 0,
    shipper_id BIGINT NOT NULL,
    order_count INT NOT NULL,
    total_amount DECIMAL(12,0) NOT NULL,
    declared_amount DECIMAL(12,0) NOT NULL,
    status ENUM('SUBMITTED','CONFIRMED','REJECTED') NOT NULL,
    shipper_note VARCHAR(500),
    manager_note VARCHAR(500),
    reviewed_by BIGINT,
    submitted_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_cod_settlement_shipper_time (shipper_id, submitted_at),
    INDEX idx_cod_settlement_status_time (status, submitted_at),
    CONSTRAINT fk_cod_settlement_shipper FOREIGN KEY (shipper_id) REFERENCES app_user(id),
    CONSTRAINT fk_cod_settlement_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cod_settlement_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(12,0) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cod_item_order UNIQUE (order_id),
    INDEX idx_cod_item_settlement (settlement_id),
    CONSTRAINT fk_cod_item_settlement FOREIGN KEY (settlement_id) REFERENCES cod_settlement(id),
    CONSTRAINT fk_cod_item_order FOREIGN KEY (order_id) REFERENCES shipment_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
