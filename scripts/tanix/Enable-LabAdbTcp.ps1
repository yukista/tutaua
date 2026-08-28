[CmdletBinding()]
param(
    [string]$CurrentTarget = '192.168.1.143:5555',
    [string]$DeviceAddress = '192.168.1.143',
    [ValidateRange(1024, 65535)]
    [int]$Port = 5555,
    [string]$Adb = 'sdk\platform-tools\adb.exe'
)

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path

if ($Port -ne 5555) {
    throw 'La ROM Tanix fixa el port del trigger del fabricant a 5555.'
}
& $adbPath -s $CurrentTarget shell setprop persist.vendor.adb.enable 1
if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut activar l ADB persistent del fabricant.' }
Start-Sleep -Seconds 3
& $adbPath connect "${DeviceAddress}:$Port" | Out-Host
$devices = & $adbPath devices
if ($devices -notmatch ([regex]::Escape("${DeviceAddress}:$Port") + '\s+device')) {
    throw "ADB TCP no respon a ${DeviceAddress}:$Port"
}

& $adbPath -s "${DeviceAddress}:$Port" shell `
    'id; getprop persist.vendor.adb.enable; getprop service.adb.tcp.port'
