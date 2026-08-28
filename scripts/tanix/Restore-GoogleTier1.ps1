[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe', [string]$Target = '192.168.1.143:5555')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$packages = @(
    'com.google.android.atv.widget',
    'com.google.android.backdrop',
    'com.google.android.syncadapters.calendar',
    'com.google.android.syncadapters.contacts',
    'com.google.android.landscape',
    'com.google.android.onetimeinitializer',
    'com.google.android.partnersetup',
    'com.google.android.tungsten.setupwraith',
    'com.google.android.tvrecommendations',
    'com.google.android.leanbacklauncher'
)

foreach ($package in $packages) {
    & $adbPath -s $Target shell pm enable --user 0 $package
    if ($LASTEXITCODE -ne 0) { throw "No s ha pogut restaurar $package" }
}
