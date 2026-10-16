# File mẫu và bảo vệ mật khẩu

Chỉ commit `.example` có placeholder và `.gitignore`. Không điền giá trị thật vào file mẫu, tài liệu, mã nguồn hoặc lệnh commit.

## File cần điền

| File mẫu được đưa lên Git | File riêng để điền giá trị thật |
| --- | --- |
| `config/aiven-local.properties.example` | `config/aiven-local.properties` |
| `src/main/resources/application-local.properties.example` | `src/main/resources/application-local.properties` |

File đầu chứa kết nối Aiven MySQL và truststore TLS. File thứ hai chứa SMTP và JWT, không chứa cấu hình database. Tất cả thành viên dùng cùng dịch vụ/database Aiven; đường dẫn truststore tùy máy. Chữ `local` chỉ file cấu hình trên máy, không phải database local.

Trên máy chưa có cấu hình, chạy tại thư mục project; các lệnh này không ghi đè file đã điền:

```powershell
if (!(Test-Path -LiteralPath config/aiven-local.properties)) {
    Copy-Item config/aiven-local.properties.example config/aiven-local.properties
}
if (!(Test-Path -LiteralPath src/main/resources/application-local.properties)) {
    Copy-Item src/main/resources/application-local.properties.example src/main/resources/application-local.properties
}
```

Điền giá trị thật trong hai file private bên phải, không sửa `.example`. File `.properties` không cần dấu nháy quanh giá trị. Dùng dấu `/` trong đường dẫn Windows; một dấu backslash trong mật khẩu phải viết thành `\\`. Không đưa mật khẩu vào chat, screenshot, tham số dòng lệnh hoặc log.

## Kiểm tra trước khi commit

```powershell
git check-ignore -v -- config/aiven-local.properties src/main/resources/application-local.properties config/certificates/aiven-truststore.p12
git ls-files -- config/aiven-local.properties src/main/resources/application-local.properties config/certificates
git diff --cached --name-only
```

Lệnh đầu phải báo các file private bị bỏ qua. Lệnh thứ hai không được liệt kê file private nào. Lệnh cuối chỉ hiện tên file đã stage, không in mật khẩu; nếu có file private, dừng trước khi commit.

`.gitignore` cũng chặn các bản sao như `aiven-local.properties.bak`, `application-local.properties.backup`, `.env`, `.env.*`, `*.env`, cùng file PEM/key/PKCS12/JKS. File `.example` vẫn được phép commit. Ứng dụng **không tự đọc `.env`**; hãy dùng hai file private trên hoặc biến môi trường hệ điều hành đúng tên.

`.gitignore` không gỡ file đã được Git theo dõi và không xóa bí mật khỏi lịch sử commit. Không dùng `git add -f` với file private. Nếu mật khẩu/token từng bị commit hoặc chia sẻ, phải đổi/thu hồi tại Aiven/nhà cung cấp tương ứng; thêm `.gitignore` không làm mật khẩu cũ an toàn trở lại. Không tự ý rewrite lịch sử chung của nhóm.

## Commit riêng cho bảo vệ cấu hình

Nếu chưa stage thay đổi khác, dùng:

```powershell
git add -- .gitignore config/aiven-local.properties.example src/main/resources/application-local.properties.example docs/thang/SECRETS_AND_EXAMPLES.md
git commit -q -m "chore(security): bao ve cau hinh va mat khau"
```

`-q` chỉ giảm output của commit, không gộp chức năng. Các chức năng còn lại commit riêng theo [kế hoạch hiện tại](COMMIT_PLAN_2026_10_10.md); không dùng `git add .` khi workspace còn nhiều nhóm thay đổi.
