[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe', [string]$Target = '192.168.1.143:5555')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
foreach ($package in @('com.google.android.gsf', 'com.google.android.gms')) {
    & $adbPath -s $Target shell pm enable --user 0 $package | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "No s ha pogut restaurar $package." }
}
