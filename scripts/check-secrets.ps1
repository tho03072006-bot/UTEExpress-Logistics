param([switch]$WorkingTree)
$ErrorActionPreference='Stop'
$repositoryRoot=(& git rev-parse --show-toplevel)
if($LASTEXITCODE -ne 0){throw 'Run this script inside the UTEExpress Git repository.'}
$files=if($WorkingTree){@(& git ls-files -- '*.properties' '*.properties.example' '*.env' '.env*')}else{@(& git diff --cached --name-only --diff-filter=ACMR)}
$failures=0
foreach($file in $files){
    if($file -notmatch '(\.properties(?:\.example)?$|(^|/)\.env(?:\.|$)|\.env$)'){continue}
    $lines=if($WorkingTree){if(Test-Path -LiteralPath (Join-Path $repositoryRoot $file)){Get-Content -LiteralPath (Join-Path $repositoryRoot $file) -Encoding UTF8}}else{@(& git show (':'+$file) 2>$null)}
    foreach($line in $lines){
        if($line -notmatch '^\s*([^#!=]*(?:password|secret|api[-_.]?key|signing[-_.]?key)[^=]*)=(.*)$'){continue}
        $key=$Matches[1].Trim();$value=$Matches[2].Trim()
        if($value -and $value -notmatch '^\$\{' -and $value -notmatch '^CHANGE_ME(?:[^A-Za-z0-9]|$)'){
            Write-Output ('BLOCKED: '+$file+' contains a literal value for '+$key+'. Value was not printed.');$failures++
        }
    }
}
if($failures -gt 0){Write-Output 'Move private values to ignored local configuration or environment variables before committing.';exit 1}
Write-Output 'OK: inspected configuration contains placeholders only. No credential values were printed.'
