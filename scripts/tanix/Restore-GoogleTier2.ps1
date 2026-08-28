[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe', [string]$Target = '192.168.1.143:5555')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
foreach ($package in @(
    'com.android.vending',
    'com.google.android.configupdater',
    'com.google.android.backuptransport'
)) {
    & $adbPath -s $Target shell pm enable --user 0 $package
    if ($LASTEXITCODE -ne 0) { throw "No s ha pogut restaurar $package" }
}
