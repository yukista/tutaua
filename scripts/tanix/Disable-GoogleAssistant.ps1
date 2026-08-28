[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe', [string]$Target = '192.168.1.143:5555')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$package = 'com.google.android.katniss'
$alreadyDisabled = (& $adbPath -s $Target shell pm list packages -d --user 0) -contains "package:$package"

try {
    if (-not $alreadyDisabled) {
        & $adbPath -s $Target shell pm disable-user --user 0 $package | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut desactivar Katniss.' }
        & $adbPath -s $Target shell am force-stop --user 0 $package | Out-Null
    }
    $resolvedHomeActivity = (& $adbPath -s $Target shell cmd package resolve-activity --brief --user 0 `
        -a android.intent.action.MAIN -c android.intent.category.HOME) -join "`n"
    $settingsActivity = (& $adbPath -s $Target shell cmd package resolve-activity --brief `
        -a android.settings.SETTINGS) -join "`n"
    if ($resolvedHomeActivity -notmatch 'com\.yukista\.tutaua' -or
        $settingsActivity -notmatch 'com\.android\.tv\.settings') {
        throw 'Validació HOME o Settings fallida.'
    }
    [pscustomobject]@{Package=$package;Disabled=$true;Home=$resolvedHomeActivity;Settings=$settingsActivity}
} catch {
    if (-not $alreadyDisabled) { & $adbPath -s $Target shell pm enable --user 0 $package | Out-Host }
    throw "Katniss restaurat automàticament: $($_.Exception.Message)"
}
