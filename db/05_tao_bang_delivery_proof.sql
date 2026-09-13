/*
 * UTEExpress - tao bang luu bang chung giao hang.
 *
 * Tach bang rieng de khong them cot vao shipment_order, tranh conflict
 * voi entity Order.java dung chung cua ca nhom.
 *
 * Script co the chay lai nhieu lan.
 */

USE uteexpress;
GO

IF OBJECT_ID(N'dbo.delivery_proof', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.delivery_proof (
        id bigint IDENTITY(1, 1) NOT NULL,
        order_id bigint NOT NULL,
        shipper_id bigint NOT NULL,
        proof_image_path varchar(255) NOT NULL,
        signature_path varchar(255) NOT NULL,
        created_at datetime2 NOT NULL
            CONSTRAINT DF_delivery_proof_created_at DEFAULT SYSDATETIME(),
        updated_at datetime2 NOT NULL
            CONSTRAINT DF_delivery_proof_updated_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_delivery_proof PRIMARY KEY (id),
        CONSTRAINT UQ_delivery_proof_order UNIQUE (order_id),
        CONSTRAINT FK_delivery_proof_order
            FOREIGN KEY (order_id)
            REFERENCES dbo.shipment_order(id),
        CONSTRAINT FK_delivery_proof_shipper
            FOREIGN KEY (shipper_id)
            REFERENCES dbo.app_user(id)
    );
END;
GO

SELECT
    id,
    order_id,
    shipper_id,
    proof_image_path,
    signature_path,
    created_at,
    updated_at
FROM dbo.delivery_proof;
GO
