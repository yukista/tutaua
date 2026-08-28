[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe', [string]$Target = '192.168.1.143:5555')

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$packages = @('com.google.android.gms', 'com.google.android.gsf')
$changedPackages = [System.Collections.Generic.List[string]]::new()

try {
    foreach ($package in $packages) {
        $alreadyDisabled = (& $adbPath -s $Target shell pm list packages -d --user 0) -contains "package:$package"
        if (-not $alreadyDisabled) {
            & $adbPath -s $Target shell pm disable-user --user 0 $package | Out-Host
            if ($LASTEXITCODE -ne 0) { throw "No s ha pogut desactivar $package." }
            $changedPackages.Add($package)
            & $adbPath -s $Target shell am force-stop --user 0 $package | Out-Null
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

    [pscustomobject]@{
        Packages = $packages -join ', '
        Disabled = $true
        Home = $resolvedHomeActivity
        Settings = $settingsActivity
        WebView = 'com.android.webview'
    }
} catch {
    foreach ($package in $changedPackages) {
        & $adbPath -s $Target shell pm enable --user 0 $package | Out-Host
    }
    throw "Nucli Google restaurat automàticament: $($_.Exception.Message)"
}
