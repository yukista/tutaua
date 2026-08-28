[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$stockLauncher = 'com.google.android.apps.tv.launcherx'
$tutaua = 'com.yukista.tutaua'

if (-not ((& $adbPath devices) -match '\sdevice$')) {
    throw 'No hi ha cap dispositiu ADB autoritzat.'
}

$candidates = (& $adbPath shell cmd package query-activities --brief `
    -a android.intent.action.MAIN -c android.intent.category.HOME) -join "`n"
if ($candidates -notmatch 'com\.yukista\.tutaua') {
    throw 'Tutaua no declara cap activitat HOME; no es canvia el launcher.'
}

try {
    & $adbPath shell pm disable-user --user 0 $stockLauncher | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut desactivar el launcher stock.' }

    $resolvedHomeActivity = (& $adbPath shell cmd package resolve-activity --brief --user 0 `
        -a android.intent.action.MAIN -c android.intent.category.HOME) -join "`n"
    if ($resolvedHomeActivity -notmatch 'com\.yukista\.tutaua') {
        throw "HOME no resol Tutaua: $resolvedHomeActivity"
    }

    & $adbPath shell input keyevent KEYCODE_HOME
    Start-Sleep -Seconds 2
    $focusedActivity = (& $adbPath shell `
        "dumpsys activity activities | grep -m1 topResumedActivity") -join ''
    if ($focusedActivity -notmatch 'com\.yukista\.tutaua') {
        throw "Tutaua no ha rebut el focus: $focusedActivity"
    }
    [pscustomobject]@{Home=$resolvedHomeActivity;Focus=$focusedActivity}
} catch {
    & $adbPath shell pm enable --user 0 $stockLauncher | Out-Host
    & $adbPath shell cmd package set-home-activity --user 0 $stockLauncher | Out-Host
    & $adbPath shell am start -n "$tutaua/.SplashActivity" | Out-Host
    throw "Launcher stock restaurat automàticament: $($_.Exception.Message)"
}
