[CmdletBinding()]
param(
    [string]$Target = '192.168.1.143:5555',
    [string]$Adb = 'sdk\platform-tools\adb.exe'
)

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path

Write-Warning 'ADB TCP es tancarà ara; la desconnexió és esperada.'
& $adbPath -s $Target shell setprop persist.vendor.adb.enable 0
if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut desactivar l ADB persistent del fabricant.' }
