[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        'com.core.rss',
        'com.hopper.rsshub.stb',
        'com.android.assistant.setting.pro',
        'com.systemguard.android'
    )]
    [string]$Package,
    [string]$Adb = 'sdk\platform-tools\adb.exe'
)

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path

function Invoke-Adb([string[]]$Arguments) {
    $result = & $adbPath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($Arguments -join ' ') ha fallat: $result" }
    return $result
}

if (-not ((& $adbPath devices) -match '\sdevice$')) {
    throw 'No hi ha cap dispositiu ADB autoritzat.'
}

$wasDisabled = (& $adbPath shell pm list packages -d --user 0) -contains "package:$Package"
if ($wasDisabled) {
    Write-Host "$Package ja estava desactivat; no s'ha canviat res."
    exit 0
}

try {
    Invoke-Adb @('shell', 'pm', 'disable-user', '--user', '0', $Package) | Out-Host
    Invoke-Adb @('shell', 'am', 'force-stop', '--user', '0', $Package) | Out-Null

    $disabled = (& $adbPath shell pm list packages -d --user 0) -contains "package:$Package"
    $tutaua = (& $adbPath shell pm path com.yukista.tutaua) -match '^package:'
    $settings = (& $adbPath shell cmd package resolve-activity --brief -a android.settings.SETTINGS) -join ''
    $resolvedHomeActivity = (& $adbPath shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME) -join ''
    if (-not $disabled -or -not $tutaua -or $settings -match 'No activity found' -or -not $settings) {
        throw "Comprovació fallida (disabled=$disabled, tutaua=$tutaua, settings=$settings, home=$resolvedHomeActivity)"
    }
    [pscustomobject]@{Package=$Package;Disabled=$disabled;Settings=$settings;Home=$resolvedHomeActivity}
} catch {
    & $adbPath shell pm enable --user 0 $Package | Out-Host
    throw "S'ha restaurat $Package automàticament. Error original: $($_.Exception.Message)"
}
