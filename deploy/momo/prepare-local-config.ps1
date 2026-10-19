$ErrorActionPreference = 'Stop'
$momoWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$momoConfig = Join-Path $momoWorkspace 'config/momo-local.properties'
if (-not (Test-Path -LiteralPath $momoConfig)) {
    Copy-Item -LiteralPath (Join-Path $momoWorkspace 'config/momo-local.properties.example') -Destination $momoConfig
}
$momoContent = [IO.File]::ReadAllText($momoConfig)
if ($momoContent -match '(?m)^app\.momo\.sim-secret=(?:CHANGE_ME)?\s*$') {
    $momoBytes = New-Object byte[] 32
    $momoRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $momoRandom.GetBytes($momoBytes) } finally { $momoRandom.Dispose() }
    $momoKey = [Convert]::ToBase64String($momoBytes)
    $momoContent = [regex]::Replace($momoContent, '(?m)^app\.momo\.sim-secret=(?:CHANGE_ME)?\s*$', ('app.momo.sim-secret=' + $momoKey))
    [IO.File]::WriteAllText($momoConfig, $momoContent, [Text.UTF8Encoding]::new($false))
    [Array]::Clear($momoBytes, 0, $momoBytes.Length)
    Write-Output 'Da tao khoa ngau nhien rieng. Khong ghi khoa ra terminal hoac Git.'
} else { Write-Output 'Da co khoa rieng; giu nguyen, khong thay khoa dang su dung.' }
Write-Output ('Dien URL Render va xem khoa de cau hinh MOMO_SIM_SECRET tai: ' + $momoConfig)
