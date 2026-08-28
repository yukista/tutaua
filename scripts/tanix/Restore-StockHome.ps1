[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$stockLauncher = 'com.google.android.apps.tv.launcherx'

if (-not ((& $adbPath devices) -match '\sdevice$')) {
    throw 'No hi ha cap dispositiu ADB autoritzat.'
}

& $adbPath shell pm enable --user 0 $stockLauncher
if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut reactivar el launcher stock.' }
& $adbPath shell cmd package set-home-activity --user 0 $stockLauncher
if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut restaurar HOME stock.' }
