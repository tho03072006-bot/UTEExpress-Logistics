# Script SQL Server cũ (không dùng nữa)

Các file trong thư mục này viết cho SQL Server, từ thời mỗi máy chạy một database riêng (tháng 9/2026).
Project hiện dùng **MySQL 8 trên Aiven**, nên các script này **không chạy được** và không cần chạy.

- Dữ liệu mẫu cho MySQL: `db/mysql/01_seed_du_lieu_mau.sql`.
- Đổi cấu trúc bảng trên database chung: dùng công cụ trong `tool/database` (`--check` rồi `--apply`), xem README ở thư mục gốc.

Giữ lại để tra cứu lịch sử thay đổi schema. `backup-du-lieu.ps1` vẫn ghi bản sao vào `db/backup/` (đã nằm trong `.gitignore`).
