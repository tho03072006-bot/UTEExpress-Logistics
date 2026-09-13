/*
 * UTEExpress - nang quyen cho cac tai khoan test theo vai tro.
 *
 * VI SAO PHAI LAM BANG TAY:
 *   Form dang ky luon gan role USER (AuthServiceImpl.register). Chuc nang phan quyen
 *   nguoi dung la phan cua TV3 (Manager/Admin) - chua lam. Trong luc cho, moi nguoi tu
 *   nang quyen bang script nay de co tai khoan test cho ca 5 vai tro.
 *
 * QUAN TRONG - PHAI DANG KY QUA GIAO DIEN TRUOC, KHONG INSERT THANG BANG SQL:
 *   Mat khau phai duoc ma hoa BCrypt boi ung dung. Neu INSERT thang chuoi mat khau
 *   vao bang thi khong dang nhap duoc, va lam hong luon luong OTP.
 *
 * CAC BUOC (lam 1 lan tren may cua tung nguoi):
 *   1. Chay app, vao http://localhost:8080/dang-ky, dang ky lan luot 3 tai khoan:
 *        admin     / admin@uteexpress.test
 *        manager1  / manager1@uteexpress.test
 *        shipper1  / shipper1@uteexpress.test
 *      Mat khau ca 3 dat giong nhau cho de nho, vi du: Test@12345
 *   2. Sau moi lan dang ky, man hinh se hoi ma OTP. Chua cau hinh SMTP that thi
 *      xem ma OTP in ra o console log cua app, nhap vao de kich hoat tai khoan.
 *      (Hoac tra trong DB: SELECT username, otp_code FROM app_user;)
 *   3. Chay script nay de nang quyen.
 *   4. Dang xuat roi dang nhap lai - role chi duoc doc luc dang nhap.
 *
 * CACH CHAY:
 *   sqlcmd -S "localhost,1433" -U sa -P <mat_khau> -d uteexpress -C -i db/02_nang_quyen_tai_khoan_test.sql
 *   Hoac mo file nay bang SSMS, chon database uteexpress roi bam Execute.
 *
 * LUU Y BAO MAT: script nay CO Y khong chua mat khau va khong chua chuoi bam BCrypt nao.
 * Repo dang de Public - khong bao gio commit mat khau that hay chuoi bam cua tai khoan
 * co quyen quan tri len GitHub, du chi la tai khoan test.
 */

USE uteexpress;
GO

UPDATE dbo.app_user SET role = 'ADMIN'   WHERE username = 'admin';
UPDATE dbo.app_user SET role = 'MANAGER' WHERE username = 'manager1';
UPDATE dbo.app_user SET role = 'SHIPPER' WHERE username = 'shipper1';
GO

-- Kiem tra lai: ca 3 phai co dung role va enabled = 1 (da kich hoat OTP)
SELECT  username,
        role,
        enabled,
        CASE WHEN enabled = 1 THEN N'San sang dang nhap'
             ELSE N'CHUA kich hoat OTP - dang nhap se bi tu choi' END AS ghi_chu
FROM    dbo.app_user
WHERE   username IN ('admin', 'manager1', 'shipper1')
ORDER BY username;
GO
