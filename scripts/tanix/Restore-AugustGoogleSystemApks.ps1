[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Target = '192.168.1.135:5555',
    [string]$Adb = 'sdk\platform-tools\adb.exe'
)

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$paths = @(
    '/system/priv-app/ConfigUpdater/ConfigUpdater.apk',
    '/system/priv-app/GoogleBackupTransport/GoogleBackupTransport.apk',
    '/system/priv-app/Katniss/Katniss.apk',
    '/system/priv-app/AtvRemoteService/AtvRemoteService.apk',
    '/system/priv-app/PrebuiltGmsCorePano/PrebuiltGmsCorePano.apk',
    '/system/priv-app/GoogleServicesFramework/GoogleServicesFramework.apk'
)

if (-not $PSCmdlet.ShouldProcess($Target, 'Restaurar les sis APK Google de la build agost')) { return }
& $adbPath -s $Target root | Out-Host
& $adbPath -s $Target remount | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut muntar overlayfs en escriptura.' }
foreach ($path in $paths) {
    $disabled = "$path.tutaua-disabled"
    & $adbPath -s $Target shell test -f $disabled
    if ($LASTEXITCODE -ne 0) { throw "No existeix $disabled" }
    & $adbPath -s $Target shell mv $disabled $path | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "No s ha pogut restaurar $path" }
}
& $adbPath -s $Target shell sync
& $adbPath -s $Target reboot
