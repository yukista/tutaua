[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$packages = @(
    'com.core.rss',
    'com.hopper.rsshub.stb',
    'com.android.assistant.setting.pro',
    'com.systemguard.android'
)

if (-not ((& $adbPath devices) -match '\sdevice$')) {
    throw 'No hi ha cap dispositiu ADB autoritzat.'
}

foreach ($package in $packages) {
    & $adbPath shell pm enable --user 0 $package
    if ($LASTEXITCODE -ne 0) { throw "No s'ha pogut restaurar $package" }
}
