/*
 * UTEExpress - tao bang luu yeu cau doi email dang cho xac thuc OTP (TV1).
 *
 * Tach bang rieng de khong them cot vao app_user, tranh conflict voi entity
 * AppUser.java dung chung cua ca nhom. Hai cot otp_code / otp_expiry co san trong
 * app_user dang duoc dung cho OTP dang ky va OTP quen mat khau, khong dung chung
 * duoc; luong nay con phai giu tam dia chi email moi trong luc cho xac thuc.
 *
 * Cot new_email de varchar (khong phai nvarchar) cho khop kieu voi app_user.email,
 * dia chi email chi gom ky tu ASCII nen khong can nvarchar.
 *
 * Script co the chay lai nhieu lan.
 */

USE uteexpress;
GO

IF OBJECT_ID(N'dbo.email_change_request', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.email_change_request (
        id bigint IDENTITY(1, 1) NOT NULL,
        user_id bigint NOT NULL,
        new_email varchar(100) NOT NULL,
        otp_code varchar(10) NOT NULL,
        expires_at datetime2 NOT NULL,
        attempt_count int NOT NULL
            CONSTRAINT DF_email_change_request_attempt_count DEFAULT 0,
        created_at datetime2 NOT NULL
            CONSTRAINT DF_email_change_request_created_at DEFAULT SYSDATETIME(),
        updated_at datetime2 NOT NULL
            CONSTRAINT DF_email_change_request_updated_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_email_change_request PRIMARY KEY (id),
        -- Moi tai khoan chi giu mot yeu cau dang cho; yeu cau moi ghi de yeu cau cu.
        CONSTRAINT UQ_email_change_request_user UNIQUE (user_id),
        CONSTRAINT FK_email_change_request_user
            FOREIGN KEY (user_id)
            REFERENCES dbo.app_user(id)
    );
END;
GO

SELECT
    id,
    user_id,
    new_email,
    otp_code,
    expires_at,
    attempt_count,
    created_at,
    updated_at
FROM dbo.email_change_request;
GO
