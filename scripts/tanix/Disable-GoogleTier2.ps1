[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe', [string]$Target = '192.168.1.143:5555')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$packages = @(
    'com.android.vending',
    'com.google.android.configupdater',
    'com.google.android.backuptransport'
)
$changed = [System.Collections.Generic.List[string]]::new()

try {
    foreach ($package in $packages) {
        $alreadyDisabled = (& $adbPath -s $Target shell pm list packages -d --user 0) -contains "package:$package"
        if (-not $alreadyDisabled) {
            & $adbPath -s $Target shell pm disable-user --user 0 $package | Out-Host
            if ($LASTEXITCODE -ne 0) { throw "No s ha pogut desactivar $package" }
            & $adbPath -s $Target shell am force-stop --user 0 $package | Out-Null
            $changed.Add($package)
        }
    }

    $resolvedHomeActivity = (& $adbPath -s $Target shell cmd package resolve-activity --brief --user 0 `
        -a android.intent.action.MAIN -c android.intent.category.HOME) -join "`n"
    $settingsActivity = (& $adbPath -s $Target shell cmd package resolve-activity --brief `
        -a android.settings.SETTINGS) -join "`n"
    $webViewState = (& $adbPath -s $Target shell dumpsys webviewupdate) -join "`n"
    if ($resolvedHomeActivity -notmatch 'com\.yukista\.tutaua' -or
        $settingsActivity -notmatch 'com\.android\.tv\.settings' -or
        $webViewState -notmatch 'Current WebView package.*com\.android\.webview') {
        throw 'Validació HOME, Settings o WebView fallida.'
    }
    [pscustomobject]@{Changed=$changed.Count;Home=$resolvedHomeActivity;Settings=$settingsActivity}
} catch {
    foreach ($package in $changed) {
        & $adbPath -s $Target shell pm enable --user 0 $package | Out-Host
    }
    throw "Google Tier 2 restaurat automàticament: $($_.Exception.Message)"
}
