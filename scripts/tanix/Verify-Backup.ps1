[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$BackupDirectory = "device-backups\TX5-20260820-143837"
)

$ErrorActionPreference = "Stop"
$backupPath = (Resolve-Path -LiteralPath $BackupDirectory).Path
$manifestPath = Join-Path $backupPath "SHA256SUMS.txt"

if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "No s'ha trobat el manifest: $manifestPath"
}

$failures = 0
$checked = 0

foreach ($line in Get-Content -LiteralPath $manifestPath) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
        continue
    }

    if ($line -notmatch '^([0-9A-Fa-f]{64})\s+(.+)$') {
        Write-Error "Línia de manifest no vàlida: $line" -ErrorAction Continue
        $failures++
        continue
    }

    $expected = $Matches[1].ToUpperInvariant()
    $name = $Matches[2]
    $filePath = Join-Path $backupPath $name
    $checked++

    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        Write-Host "MISSING  $name" -ForegroundColor Red
        $failures++
        continue
    }

    $actual = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash
    if ($actual -eq $expected) {
        Write-Host "OK       $name"
    } else {
        Write-Host "MISMATCH $name" -ForegroundColor Red
        Write-Host "  esperat: $expected"
        Write-Host "  actual:  $actual"
        $failures++
    }
}

$manifestNames = Get-Content -LiteralPath $manifestPath |
    Where-Object { $_ -match '^([0-9A-Fa-f]{64})\s+(.+)$' } |
    ForEach-Object { if ($_ -match '^([0-9A-Fa-f]{64})\s+(.+)$') { $Matches[2] } }

$untrackedImages = Get-ChildItem -LiteralPath $backupPath -File |
    Where-Object { $_.Extension -eq ".img" -and $_.Name -notin $manifestNames }

foreach ($file in $untrackedImages) {
    Write-Host "UNLISTED $($file.Name)" -ForegroundColor Yellow
    $failures++
}

Write-Host "Comprovats: $checked; errors: $failures"
if ($failures -ne 0) {
    exit 1
}
