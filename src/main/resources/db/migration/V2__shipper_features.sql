-- Chỉ thêm bảng mới; không sửa dữ liệu hay cột của Order/AppUser.
CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(100) NOT NULL,
    previous_status ENUM('PENDING_PICKUP','PICKED_UP','IN_TRANSIT','DELIVERED','FAILED','CANCELLED','RETURNED') NOT NULL,
    status ENUM('PENDING_PICKUP','PICKED_UP','IN_TRANSIT','DELIVERED','FAILED','CANCELLED','RETURNED') NOT NULL,
    failure_reason ENUM('CUSTOMER_ABSENT','PHONE_UNREACHABLE','WRONG_ADDRESS','REFUSED','DAMAGED','OTHER'),
    note VARCHAR(500),
    next_attempt_at DATETIME(6),
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_journey_order_time (order_id, occurred_at, id),
    CONSTRAINT fk_journey_order FOREIGN KEY (order_id) REFERENCES shipment_order(id),
    CONSTRAINT fk_journey_actor FOREIGN KEY (actor_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS shipper_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    message VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_order_recipient UNIQUE (order_id, recipient_id),
    INDEX idx_notification_recipient_read (recipient_id, read_at, created_at),
    CONSTRAINT fk_notification_order FOREIGN KEY (order_id) REFERENCES shipment_order(id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_complaint (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision BIGINT NOT NULL DEFAULT 0,
    order_id BIGINT NOT NULL,
    subject VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    image_bytes MEDIUMBLOB,
    status ENUM('OPEN','IN_PROGRESS','RESOLVED','REJECTED') NOT NULL,
    manager_reply VARCHAR(2000),
    shipper_note VARCHAR(1000),
    handled_by BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_complaint_order UNIQUE (order_id),
    INDEX idx_complaint_status_time (status, created_at),
    CONSTRAINT fk_complaint_order FOREIGN KEY (order_id) REFERENCES shipment_order(id),
    CONSTRAINT fk_complaint_handler FOREIGN KEY (handled_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
