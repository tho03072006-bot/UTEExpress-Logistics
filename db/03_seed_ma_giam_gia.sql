/*
 * UTEExpress - du lieu mau cho bang ma giam gia (promo_code).
 *
 * VI SAO CAN SCRIPT NAY:
 *   Man hinh tao/sua ma giam gia thuoc phan cua TV3 (Manager - quan ly khuyen mai),
 *   chua lam. Script nay nap san vai ma de test va demo chuc nang "ap ma giam cuoc"
 *   ben phia nguoi gui (TV1 - viec 5). Khi TV3 lam xong man hinh quan ly thi khong
 *   can script nay nua.
 *
 * CACH CHAY:
 *   sqlcmd -S "localhost,1433" -U sa -P <mat_khau> -d uteexpress -C -f 65001 -i db/03_seed_ma_giam_gia.sql
 *   Hoac mo bang SSMS, chon database uteexpress roi bam Execute.
 *
 * CHU Y CO "-f 65001": file nay luu dang UTF-8 va co tieng Viet co dau trong phan mo ta.
 * sqlcmd mac dinh doc file .sql theo bang ma ANSI cua Windows, thieu co -f 65001 thi
 * "Giam 10% cuoc" co dau se bi luu sai thanh ky tu la. File da co san dau BOM nen
 * phan lon truong hop sqlcmd tu nhan ra UTF-8, nhung cu ghi ro co cho chac.
 *
 * Chay lai nhieu lan cung khong sao: ma nao da co thi bo qua, khong tao trung.
 * Bang promo_code do Hibernate tu tao khi chay app (ddl-auto=update), nho chay app
 * it nhat mot lan truoc khi chay script nay.
 */

USE uteexpress;
GO

-- Giam 10% cuoc, toi da 20.000d, khong yeu cau cuoc toi thieu
IF NOT EXISTS (SELECT 1 FROM dbo.promo_code WHERE code = 'UTE10')
INSERT INTO dbo.promo_code
    (code, description, discount_type, discount_value, max_discount_amount, min_order_amount,
     start_at, end_at, usage_limit, used_count, active, created_at, updated_at)
VALUES
    ('UTE10', N'Giảm 10% cước, tối đa 20.000đ', 'PERCENT', 10, 20000, NULL,
     SYSDATETIME(), DATEADD(month, 6, SYSDATETIME()), 100, 0, 1, SYSDATETIME(), SYSDATETIME());
GO

-- Giam thang 15.000d cho don co cuoc tu 30.000d tro len
IF NOT EXISTS (SELECT 1 FROM dbo.promo_code WHERE code = 'FREESHIP15')
INSERT INTO dbo.promo_code
    (code, description, discount_type, discount_value, max_discount_amount, min_order_amount,
     start_at, end_at, usage_limit, used_count, active, created_at, updated_at)
VALUES
    ('FREESHIP15', N'Giảm 15.000đ cho đơn từ 30.000đ', 'FIXED_AMOUNT', 15000, NULL, 30000,
     SYSDATETIME(), DATEADD(month, 6, SYSDATETIME()), 50, 0, 1, SYSDATETIME(), SYSDATETIME());
GO

-- Ma DA HET HAN - de test truong hop bao loi het han
IF NOT EXISTS (SELECT 1 FROM dbo.promo_code WHERE code = 'HETHAN')
INSERT INTO dbo.promo_code
    (code, description, discount_type, discount_value, max_discount_amount, min_order_amount,
     start_at, end_at, usage_limit, used_count, active, created_at, updated_at)
VALUES
    ('HETHAN', N'Mã đã hết hạn (dùng để kiểm thử)', 'PERCENT', 50, NULL, NULL,
     DATEADD(month, -2, SYSDATETIME()), DATEADD(month, -1, SYSDATETIME()), NULL, 0, 1,
     SYSDATETIME(), SYSDATETIME());
GO

-- Ma DA TAT - de test truong hop bao ngung ap dung
IF NOT EXISTS (SELECT 1 FROM dbo.promo_code WHERE code = 'DATAT')
INSERT INTO dbo.promo_code
    (code, description, discount_type, discount_value, max_discount_amount, min_order_amount,
     start_at, end_at, usage_limit, used_count, active, created_at, updated_at)
VALUES
    ('DATAT', N'Mã đã tắt (dùng để kiểm thử)', 'FIXED_AMOUNT', 5000, NULL, NULL,
     SYSDATETIME(), DATEADD(month, 6, SYSDATETIME()), NULL, 0, 0, SYSDATETIME(), SYSDATETIME());
GO

SELECT code, description, discount_type, discount_value, max_discount_amount,
       min_order_amount, end_at, usage_limit, used_count, active
FROM   dbo.promo_code
ORDER BY code;
GO
