/*
 * UTEExpress - chuyen cac cot chu tu varchar sang nvarchar (ho tro tieng Viet co dau).
 *
 * VI SAO CAN SCRIPT NAY:
 *   Hibernate mac dinh sinh cot kieu varchar tren SQL Server. varchar chi chua duoc
 *   bang ma 1 byte nen chu tieng Viet co dau luu xuong se thanh dau "?"
 *   (vi du "Nguyen Van Tho" co dau se thanh "Nguy?n Van Th?").
 *   Muon luu dung phai dung nvarchar (Unicode).
 *
 *   Cac ENTITY MOI thi da xu ly bang annotation @Nationalized trong code
 *   (xem SavedAddress.java), nhung ddl-auto=update KHONG tu doi kieu cua cot da tao
 *   truoc do - nen cac bang cu phai chay script nay 1 lan de chuyen kieu.
 *
 * CACH CHAY (Git Bash hoac CMD, thay <mat_khau> bang mat khau sa tren may ban):
 *   sqlcmd -S "localhost,1433" -U sa -P <mat_khau> -d uteexpress -i db/01_chuyen_cot_chu_sang_nvarchar.sql
 * Hoac mo file nay bang SSMS, chon database uteexpress roi bam Execute.
 *
 * LUU Y:
 *   - Script nay khong lam mat du lieu, chay lai nhieu lan cung khong sao.
 *   - Nhung du lieu tieng Viet DA bi luu thanh "?" truoc do thi khong khoi phuc duoc,
 *     phai nhap lai ban ghi do (du lieu test nen nhap lai rat nhanh).
 *   - Cac cot chi chua ASCII (username, email, password, tracking_code, cac cot enum...)
 *     giu nguyen varchar, khong can doi.
 *   - Phai bao ca nhom truoc khi chay vi 2 bang app_user va shipment_order la bang dung chung.
 */

USE uteexpress;
GO

-- ===== app_user: ho ten nguoi dung co dau =====
IF COL_LENGTH('dbo.app_user', 'full_name') IS NOT NULL
    ALTER TABLE dbo.app_user ALTER COLUMN full_name nvarchar(100) NULL;
GO

-- ===== shipment_order: ten va dia chi nguoi gui / nguoi nhan =====
IF COL_LENGTH('dbo.shipment_order', 'sender_name') IS NOT NULL
    ALTER TABLE dbo.shipment_order ALTER COLUMN sender_name nvarchar(100) NULL;
GO
IF COL_LENGTH('dbo.shipment_order', 'sender_address') IS NOT NULL
    ALTER TABLE dbo.shipment_order ALTER COLUMN sender_address nvarchar(255) NULL;
GO
IF COL_LENGTH('dbo.shipment_order', 'receiver_name') IS NOT NULL
    ALTER TABLE dbo.shipment_order ALTER COLUMN receiver_name nvarchar(100) NULL;
GO
IF COL_LENGTH('dbo.shipment_order', 'receiver_address') IS NOT NULL
    ALTER TABLE dbo.shipment_order ALTER COLUMN receiver_address nvarchar(255) NULL;
GO

-- ===== saved_address: bang da lo tao bang varchar o lan chay dau tien =====
-- (tu lan sau Hibernate se tu tao dung nvarchar nho @Nationalized trong SavedAddress.java)
IF COL_LENGTH('dbo.saved_address', 'label') IS NOT NULL
    ALTER TABLE dbo.saved_address ALTER COLUMN label nvarchar(50) NOT NULL;
GO
IF COL_LENGTH('dbo.saved_address', 'contact_name') IS NOT NULL
    ALTER TABLE dbo.saved_address ALTER COLUMN contact_name nvarchar(100) NOT NULL;
GO
IF COL_LENGTH('dbo.saved_address', 'address_line') IS NOT NULL
    ALTER TABLE dbo.saved_address ALTER COLUMN address_line nvarchar(255) NOT NULL;
GO

-- Kiem tra lai ket qua: cot nao con kieu varchar thi se hien ra o day
SELECT  t.name  AS bang,
        c.name  AS cot,
        ty.name AS kieu_du_lieu,
        c.max_length
FROM    sys.columns c
        JOIN sys.tables t  ON t.object_id = c.object_id
        JOIN sys.types ty  ON ty.user_type_id = c.user_type_id
WHERE   t.name IN ('app_user', 'shipment_order', 'saved_address')
        AND ty.name IN ('varchar', 'nvarchar')
ORDER BY t.name, c.name;
GO
