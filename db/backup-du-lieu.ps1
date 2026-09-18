<#
    UTEExpress - xuat toan bo DU LIEU cua database ra mot file .sql chay lai duoc.

    DUNG DE LAM GI
      Database chung dat tren MonsterASP (goi Free). Mat mang, het han goi, hoac lo
      tay xoa nham thi khong co ban sao nao khac. Script nay keo du lieu ve may de:
        - Co ban sao phong khi su co
        - Hom demo mat mang van do nguoc vao SQL Server tren may ma chay duoc

    KHONG XUAT SCHEMA, CHI XUAT DU LIEU
      Cau lenh tao bang da nam san trong db/01 -> db/06 va tren Git roi. Muon dung
      lai tu dau thi chay cac script do truoc, roi chay file backup nay sau.

    CACH DUNG
      # Sao luu database tren may minh (dang nhap bang tai khoan Windows)
      .\db\backup-du-lieu.ps1

      # Sao luu database chung tren MonsterASP
      .\db\backup-du-lieu.ps1 -Server "HOSTNAME_REMOTE" -Database "TEN_DB" `
                              -User "LOGIN" -Password "MAT_KHAU"

      # Do nguoc file backup vao database tren may minh
      sqlcmd -S "localhost\SQLEXPRESS" -E -C -f 65001 -d uteexpress -i "db\backup\<ten-file>.sql"

    FILE KET QUA
      db/backup/uteexpress_data_<ngay>_<gio>.sql
      Thu muc db/backup/ da nam trong .gitignore vi chua du lieu that cua nguoi dung.
#>

[CmdletBinding()]
param(
    [string] $Server   = "localhost\SQLEXPRESS",
    [string] $Database = "uteexpress",
    [string] $User     = "",
    [string] $Password = "",
    [string] $OutDir   = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path $PSScriptRoot "backup"
}
if (-not (Test-Path $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}

# Dung Integrated Security khi khong dua User/Password - tien cho database tren may minh.
if ([string]::IsNullOrWhiteSpace($User)) {
    $connectionString = "Server=$Server;Database=$Database;Integrated Security=True;TrustServerCertificate=True;Connect Timeout=30"
    $cheDo = "tai khoan Windows"
} else {
    # Ket noi ra Internet thi bat ma hoa, khong de mat khau di tran.
    $connectionString = "Server=$Server;Database=$Database;User ID=$User;Password=$Password;Encrypt=True;TrustServerCertificate=True;Connect Timeout=30"
    $cheDo = "tai khoan SQL ($User)"
}

Write-Host "Ket noi toi $Server / $Database bang $cheDo ..."

$connection = New-Object System.Data.SqlClient.SqlConnection $connectionString
try {
    $connection.Open()
} catch {
    Write-Host "KHONG KET NOI DUOC: $($_.Exception.Message)"
    Write-Host ""
    Write-Host "Neu dang sao luu database tren MonsterASP, kiem tra lai da lay hostname o"
    Write-Host "tab 'Remote access' chua - hostname o tab 'Local access' chi chay duoc tu"
    Write-Host "ben trong may chu cua ho, may ca nhan khong goi toi duoc."
    exit 1
}

function Invoke-Query([string] $sql) {
    $command = $connection.CreateCommand()
    $command.CommandText = $sql
    $command.CommandTimeout = 120
    $adapter = New-Object System.Data.SqlClient.SqlDataAdapter $command
    $table = New-Object System.Data.DataTable
    [void] $adapter.Fill($table)
    # Tra ve mang DataRow chu khong phai DataTable: PowerShell 5.1 khong duyet
    # thang DataTable bang foreach duoc, phai di qua .Rows.
    return ,@($table.Rows)
}

<#
    Sap xep bang theo thu tu khoa ngoai: bang duoc tro toi phai do du lieu vao truoc.
    Vi du app_user phai co truoc shipment_order, neu khong SQL Server tu choi INSERT.

    Tinh thu tu bang vong lap thay vi viet cung danh sach: TV2 va TV3 them bang moi
    thi script van tu sap xep dung, khong ai phai nho vao day sua.
#>
function Get-TablesInDependencyOrder {
    $tables = Invoke-Query @"
SELECT s.name AS SchemaName, t.name AS TableName
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
ORDER BY t.name;
"@

    $dependencies = Invoke-Query @"
SELECT
    OBJECT_NAME(fk.parent_object_id)     AS ChildTable,
    OBJECT_NAME(fk.referenced_object_id) AS ParentTable
FROM sys.foreign_keys fk
WHERE fk.parent_object_id <> fk.referenced_object_id;
"@

    $remaining = [System.Collections.ArrayList] @()
    foreach ($row in $tables) { [void] $remaining.Add([string] $row.TableName) }

    $ordered = [System.Collections.ArrayList] @()
    $guard = 0

    while ($remaining.Count -gt 0) {
        $guard++
        if ($guard -gt 100) {
            # Khoa ngoai vong tron (A tro B, B tro A) thi khong co thu tu nao dung ca.
            Write-Host "CANH BAO: co the co khoa ngoai vong tron, do not phan con lai theo ten."
            foreach ($name in $remaining) { [void] $ordered.Add($name) }
            break
        }

        $addedThisRound = @()
        foreach ($name in $remaining) {
            $parents = $dependencies |
                Where-Object { $_.ChildTable -eq $name } |
                Select-Object -ExpandProperty ParentTable
            # Duoc do vao khi moi bang cha cua no da nam trong danh sach roi
            $stillWaiting = $parents | Where-Object { $remaining -contains $_ -and $_ -ne $name }
            if (-not $stillWaiting) {
                [void] $ordered.Add($name)
                $addedThisRound += $name
            }
        }
        foreach ($name in $addedThisRound) { $remaining.Remove($name) }
        if ($addedThisRound.Count -eq 0) { continue }
    }

    return $ordered
}

<# Doi mot gia tri sang dang viet duoc trong cau INSERT cua SQL Server. #>
function Format-SqlValue($value, [string] $sqlType) {
    if ($null -eq $value -or $value -is [System.DBNull]) {
        return "NULL"
    }
    switch -Regex ($sqlType) {
        '^(bit)$' {
            if ([bool] $value) { return "1" } else { return "0" }
        }
        '^(tinyint|smallint|int|bigint|decimal|numeric|float|real|money|smallmoney)$' {
            return ([string] $value).Replace(",", ".")
        }
        '^(date|datetime|datetime2|smalldatetime|datetimeoffset|time)$' {
            return "'" + ([datetime] $value).ToString("yyyy-MM-ddTHH:mm:ss.fff") + "'"
        }
        '^(uniqueidentifier)$' {
            return "'" + $value.ToString() + "'"
        }
        '^(varbinary|binary|image)$' {
            return "0x" + [System.BitConverter]::ToString($value).Replace("-", "")
        }
        default {
            # Tien to N de tieng Viet co dau khong bi hong khi do lai vao cot nvarchar.
            # Dau nhay don trong noi dung phai nhan doi, neu khong cau lenh se vo.
            return "N'" + ([string] $value).Replace("'", "''") + "'"
        }
    }
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmm"
$outFile = Join-Path $OutDir "uteexpress_data_$timestamp.sql"

$lines = [System.Collections.ArrayList] @()
[void] $lines.Add("/*")
[void] $lines.Add(" * UTEExpress - ban sao DU LIEU, sinh tu dong boi db/backup-du-lieu.ps1")
[void] $lines.Add(" * Nguon : $Server / $Database")
[void] $lines.Add(" * Luc   : $(Get-Date -Format 'dd/MM/yyyy HH:mm:ss')")
[void] $lines.Add(" *")
[void] $lines.Add(" * File nay CHI CHUA DU LIEU. Muon dung lai tu dau thi chay db/01 -> db/06")
[void] $lines.Add(" * de tao bang truoc, roi moi chay file nay.")
[void] $lines.Add(" *")
[void] $lines.Add(" * Chay lai:")
[void] $lines.Add(" *   sqlcmd -S `"localhost\SQLEXPRESS`" -E -C -f 65001 -d uteexpress -i `"<file nay>`"")
[void] $lines.Add(" */")
[void] $lines.Add("")
[void] $lines.Add("SET NOCOUNT ON;")
[void] $lines.Add("GO")
[void] $lines.Add("")

$orderedTables = Get-TablesInDependencyOrder
$tongSoDong = 0

foreach ($tableName in $orderedTables) {
    $columns = Invoke-Query @"
SELECT c.name AS ColumnName, ty.name AS TypeName, c.is_identity AS IsIdentity
FROM sys.columns c
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID('dbo.$tableName')
  AND c.is_computed = 0
ORDER BY c.column_id;
"@
    if ($columns.Count -eq 0) { continue }

    $columnNames = @($columns | ForEach-Object { $_.ColumnName })
    $hasIdentity = @($columns | Where-Object { $_.IsIdentity -eq $true }).Count -gt 0

    $data = Invoke-Query "SELECT * FROM dbo.[$tableName];"
    $soDong = $data.Count
    $tongSoDong += $soDong

    [void] $lines.Add("-- ===== $tableName ($soDong dong) =====")
    if ($soDong -eq 0) {
        [void] $lines.Add("-- (bang rong)")
        [void] $lines.Add("")
        Write-Host ("  {0,-26} {1,5} dong" -f $tableName, $soDong)
        continue
    }

    # Giu nguyen gia tri cot id: cac bang khac tro toi nhau bang chinh id do,
    # de SQL Server tu sinh lai thi moi lien ket giua cac bang se sai het.
    if ($hasIdentity) {
        [void] $lines.Add("SET IDENTITY_INSERT dbo.[$tableName] ON;")
    }

    $columnList = ($columnNames | ForEach-Object { "[$_]" }) -join ", "
    foreach ($row in $data) {
        $values = @()
        foreach ($column in $columns) {
            $values += (Format-SqlValue $row[$column.ColumnName] $column.TypeName)
        }
        [void] $lines.Add("INSERT INTO dbo.[$tableName] ($columnList) VALUES (" + ($values -join ", ") + ");")
    }

    if ($hasIdentity) {
        [void] $lines.Add("SET IDENTITY_INSERT dbo.[$tableName] OFF;")
    }
    [void] $lines.Add("GO")
    [void] $lines.Add("")

    Write-Host ("  {0,-26} {1,5} dong" -f $tableName, $soDong)
}

$connection.Close()

# UTF-8 co BOM de sqlcmd -f 65001 doc dung tieng Viet co dau.
$encoding = New-Object System.Text.UTF8Encoding($true)
[System.IO.File]::WriteAllLines($outFile, $lines, $encoding)

Write-Host ""
Write-Host "Xong. $($orderedTables.Count) bang, $tongSoDong dong."
Write-Host "File: $outFile"
