[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$TutauaApk,
    [Parameter(Mandatory = $true)][string]$BoxApk,
    [Parameter(Mandatory = $true)][string]$TvApk,
    [Parameter(Mandatory = $true)][string]$GamesApk,
    [Parameter(Mandatory = $true)][uri]$JellyfinServer,
    [Parameter(Mandatory = $true)][uri]$TvApiUrl,
    [uri]$TvStreamUrl,
    [uri]$UpdateManifestUrl,
    [ValidatePattern('^[A-Za-z0-9._-]{1,32}$')][string]$UpdateChannel = 'stable',
    [Parameter(Mandatory = $true)][System.Management.Automation.PSCredential]$Credential,
    [string]$Adb = 'sdk\platform-tools\adb.exe',
    [string]$ReportDirectory = 'provisioning-reports',
    [ValidateRange(1, 120)][int]$ScreenOffTimeoutMinutes = 10,
    [ValidateRange(0, 400)][int]$LibraryButtonKeyCode = 132,
    [ValidateRange(0, 400)][int]$LiveTvButtonKeyCode = 134,
    [ValidateRange(0, 400)][int]$GamesButtonKeyCode = 0,
    [bool]$GamesEnabled = $true,
    [switch]$KeepLabAdb,
    [switch]$AllowCompatibleHardware
)

$ErrorActionPreference = 'Stop'
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$tutauaApkPath = (Resolve-Path -LiteralPath $TutauaApk).Path
$boxApkPath = (Resolve-Path -LiteralPath $BoxApk).Path
$tvApkPath = (Resolve-Path -LiteralPath $TvApk).Path
$gamesApkPath = (Resolve-Path -LiteralPath $GamesApk).Path
$deviceAddress = ($Target -split ':')[0]
$stableTarget = "${deviceAddress}:5555"
$screenOffTimeoutMs = $ScreenOffTimeoutMinutes * 60000
$expected = @{
    Model = 'TX5'; Device = 'qurra'; Hardware = 'amlogic'
    Build = 'eng.fjq.20251111.100710'; MinMemoryKb = 1900000L
    MinStorageBytes = 15000000000L; MaxStorageBytes = 17000000000L
}
$packages = @(
    'com.core.rss', 'com.hopper.rsshub.stb', 'com.android.assistant.setting.pro',
    'com.systemguard.android', 'com.google.android.apps.tv.launcherx',
    'com.google.android.atv.widget', 'com.google.android.backdrop',
    'com.google.android.syncadapters.calendar', 'com.google.android.syncadapters.contacts',
    'com.google.android.landscape', 'com.google.android.onetimeinitializer',
    'com.google.android.partnersetup', 'com.google.android.tungsten.setupwraith',
    'com.google.android.tvrecommendations', 'com.google.android.leanbacklauncher',
    'com.android.vending', 'com.google.android.configupdater',
    'com.google.android.backuptransport', 'com.google.android.katniss',
    'com.google.android.tv.remote.service', 'com.google.android.gms', 'com.google.android.gsf'
)

function Invoke-Adb([string]$Serial, [string[]]$Arguments, [switch]$AllowFailure) {
    $output = & $adbPath -s $Serial @Arguments 2>&1
    if (-not $AllowFailure -and $LASTEXITCODE -ne 0) {
        throw "ADB ha fallat: adb -s $Serial $($Arguments -join ' '): $output"
    }
    return $output
}
function Get-Prop([string]$Serial, [string]$Name) {
    return ((Invoke-Adb $Serial @('shell','getprop',$Name)) -join '').Trim()
}
function ConvertTo-Base64([string]$Value) {
    if ([string]::IsNullOrEmpty($Value)) { return '-' }
    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value))
}
function Wait-ForAndroid([string]$Serial, [int]$Seconds = 120) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        & $adbPath connect $Serial 2>$null | Out-Null
        if ((& $adbPath -s $Serial get-state 2>$null) -eq 'device' -and
            (Get-Prop $Serial 'sys.boot_completed') -eq '1') { return }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Android no ha tornat a $Serial dins de $Seconds segons."
}

& $adbPath connect $Target | Out-Host
if ((& $adbPath -s $Target get-state 2>$null) -ne 'device') { throw "ADB no està autoritzat a $Target." }
$identity = [ordered]@{
    Serial = Get-Prop $Target 'ro.serialno'
    Model = Get-Prop $Target 'ro.product.model'
    Device = Get-Prop $Target 'ro.product.device'
    Hardware = Get-Prop $Target 'ro.boot.hardware'
    Build = Get-Prop $Target 'ro.build.version.incremental'
    Fingerprint = Get-Prop $Target 'ro.build.fingerprint'
    MemoryKb = [long](((Invoke-Adb $Target @('shell','cat','/proc/meminfo') | Select-String '^MemTotal:').Line -split '\s+')[1])
    StorageBytes = [long](Invoke-Adb $Target @('shell','blockdev','--getsize64','/dev/block/mmcblk0') | Select-Object -First 1)
}
$hardwareMatches = $identity.Model -eq $expected.Model -and $identity.Device -eq $expected.Device -and
    $identity.Hardware -eq $expected.Hardware -and $identity.MemoryKb -ge $expected.MinMemoryKb -and
    $identity.StorageBytes -ge $expected.MinStorageBytes -and $identity.StorageBytes -le $expected.MaxStorageBytes
$buildMatches = $identity.Build -eq $expected.Build
if (-not $hardwareMatches -or (-not $buildMatches -and -not $AllowCompatibleHardware)) {
    throw "Unitat no validada (model=$($identity.Model), device=$($identity.Device), build=$($identity.Build), RAM=$($identity.MemoryKb), eMMC=$($identity.StorageBytes))."
}
if (-not $PSCmdlet.ShouldProcess("$Target ($($identity.Serial))", 'Provisionar completament el Tanix TX5')) {
    [pscustomobject]@{Compatible=$hardwareMatches;ExactBuild=$buildMatches;Identity=$identity}
    return
}

foreach ($item in @(
    @{Name='Tutaua Box';Path=$boxApkPath},
    @{Name='Tutaua';Path=$tutauaApkPath},
    @{Name='Tutaua TV';Path=$tvApkPath},
    @{Name='Tutaua Retro';Path=$gamesApkPath}
)) {
    & $adbPath -s $Target install -r $item.Path | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "La instal·lació de $($item.Name) ha fallat." }
}
Invoke-Adb $Target @('shell','pm','grant','com.yukista.tutaua.games','android.permission.READ_EXTERNAL_STORAGE') | Out-Null
$boxRequestId = [guid]::NewGuid().ToString('N')
Invoke-Adb $Target @('logcat','-c') | Out-Null
Invoke-Adb $Target @('shell','am','broadcast','-a','com.yukista.tutaua.box.action.PROVISION',
    '-n','com.yukista.tutaua.box/.ProvisioningReceiver','--es','request_id',$boxRequestId,
    '--es','jellyfin_b64',(ConvertTo-Base64 $JellyfinServer.AbsoluteUri.TrimEnd('/')),
    '--es','tv_api_b64',(ConvertTo-Base64 $TvApiUrl.AbsoluteUri.TrimEnd('/')),
    '--es','tv_stream_b64',(ConvertTo-Base64 $(if($TvStreamUrl){$TvStreamUrl.AbsoluteUri}else{''})),
    '--es','updates_b64',(ConvertTo-Base64 $(if($UpdateManifestUrl){$UpdateManifestUrl.AbsoluteUri}else{''})),
    '--es','channel_b64',(ConvertTo-Base64 $UpdateChannel),
    '--es','screen_timeout_b64',(ConvertTo-Base64 "$ScreenOffTimeoutMinutes"),
    '--es','library_keycode_b64',(ConvertTo-Base64 "$LibraryButtonKeyCode"),
    '--es','live_tv_keycode_b64',(ConvertTo-Base64 "$LiveTvButtonKeyCode"),
    '--es','games_keycode_b64',(ConvertTo-Base64 "$GamesButtonKeyCode"),
    '--es','games_enabled_b64',(ConvertTo-Base64 "$GamesEnabled")) | Out-Null
Start-Sleep -Seconds 2
$boxResult = (Invoke-Adb $Target @('logcat','-d','-s','TUTAUA_BOX_PROVISION:I','*:S')) -join "`n"
if ($boxResult -notmatch "$boxRequestId SUCCESS") { throw 'Tutaua Box no ha acceptat la configuració.' }
$requestId = [guid]::NewGuid().ToString('N')
$plainPassword = $Credential.GetNetworkCredential().Password
try {
    Invoke-Adb $Target @('logcat','-c') | Out-Null
    Invoke-Adb $Target @('shell','am','broadcast','-a','com.yukista.tutaua.action.PROVISION',
        '-n','com.yukista.tutaua/.ProvisioningReceiver','--es','request_id',$requestId,
        '--es','server_b64',(ConvertTo-Base64 $JellyfinServer.AbsoluteUri.TrimEnd('/')),
        '--es','username_b64',(ConvertTo-Base64 $Credential.UserName),
        '--es','password_b64',(ConvertTo-Base64 $plainPassword)) | Out-Null
} finally { $plainPassword = $null }
$loginDeadline = (Get-Date).AddSeconds(40); $loginResult = ''
do {
    Start-Sleep -Seconds 2
    $loginResult = (Invoke-Adb $Target @('logcat','-d','-s','TUTAUA_PROVISION:I','*:S')) -join "`n"
} while ($loginResult -notmatch $requestId -and (Get-Date) -lt $loginDeadline)
if ($loginResult -notmatch "$requestId SUCCESS") { throw 'Tutaua no ha pogut autenticar i desar la sessió Jellyfin.' }

$installed = (Invoke-Adb $Target @('shell','pm','list','packages'))
$disabledBefore = (Invoke-Adb $Target @('shell','pm','list','packages','-d','--user','0'))
foreach ($package in $packages) {
    if ($installed -contains "package:$package" -and $disabledBefore -notcontains "package:$package") {
        Invoke-Adb $Target @('shell','pm','disable-user','--user','0',$package) | Out-Host
        Invoke-Adb $Target @('shell','am','force-stop','--user','0',$package) | Out-Null
    }
}
Invoke-Adb $Target @('shell','setprop','persist.vendor.adb.enable','1') | Out-Null
Invoke-Adb $Target @('shell','settings','put','system','screen_off_timeout',"$screenOffTimeoutMs") | Out-Null
Start-Sleep -Seconds 4
& $adbPath connect $stableTarget | Out-Null
Invoke-Adb $stableTarget @('reboot') | Out-Null
Wait-ForAndroid $stableTarget
Invoke-Adb $stableTarget @('shell','input','keyevent','HOME') | Out-Null
Start-Sleep -Seconds 3

$disabledAfter = Invoke-Adb $stableTarget @('shell','pm','list','packages','-d','--user','0')
$missingDisabled = @($packages | Where-Object { $disabledAfter -notcontains "package:$_" })
$home = (Invoke-Adb $stableTarget @('shell','cmd','package','resolve-activity','--brief','--user','0','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')) -join "`n"
$settings = (Invoke-Adb $stableTarget @('shell','cmd','package','resolve-activity','--brief','-a','android.settings.SETTINGS')) -join "`n"
$webView = (Invoke-Adb $stableTarget @('shell','dumpsys','webviewupdate')) -join "`n"
$network = (Invoke-Adb $stableTarget @('shell','ping','-c','1','-W','4','1.1.1.1') -AllowFailure) -join "`n"
$configuredScreenOffTimeout = [int](
    (Invoke-Adb $stableTarget @('shell','settings','get','system','screen_off_timeout') | Select-Object -First 1)
)
$success = $missingDisabled.Count -eq 0 -and $home -match 'com\.yukista\.tutaua\.box' -and
    $settings -match 'com\.android\.tv\.settings' -and $webView -match 'Current WebView package.*com\.android\.webview' -and
    $network -match '1 received' -and $configuredScreenOffTimeout -eq $screenOffTimeoutMs

$report = [ordered]@{Timestamp=(Get-Date).ToString('o');Success=$success;Identity=$identity;
    ExactBuild=$buildMatches;Apks=@(
        @{Package='com.yukista.tutaua.box';Path=$boxApkPath;Sha256=(Get-FileHash -LiteralPath $boxApkPath -Algorithm SHA256).Hash},
        @{Package='com.yukista.tutaua';Path=$tutauaApkPath;Sha256=(Get-FileHash -LiteralPath $tutauaApkPath -Algorithm SHA256).Hash},
        @{Package='tv.tutaua.app';Path=$tvApkPath;Sha256=(Get-FileHash -LiteralPath $tvApkPath -Algorithm SHA256).Hash},
        @{Package='com.yukista.tutaua.games';Path=$gamesApkPath;Sha256=(Get-FileHash -LiteralPath $gamesApkPath -Algorithm SHA256).Hash}
    );
    DisabledPackages=$packages;MissingDisabledPackages=$missingDisabled;Home=$home;Settings=$settings;
    WebViewAosp=($webView -match 'Current WebView package.*com\.android\.webview');Network=$network -match '1 received';
    ScreenOffTimeoutMinutes=($configuredScreenOffTimeout / 60000);
    LabAdbKept=[bool]$KeepLabAdb}
New-Item -ItemType Directory -Path $ReportDirectory -Force | Out-Null
$safeSerial = ($identity.Serial -replace '[^A-Za-z0-9_.-]','_')
$reportPath = Join-Path $ReportDirectory ("{0}-{1}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'),$safeSerial)
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding utf8
if (-not $success) { throw "La validació final ha fallat. Informe: $reportPath" }
if (-not $KeepLabAdb) {
    Write-Warning 'La validació ha acabat; ara es tancarà ADB TCP persistent.'
    Invoke-Adb $stableTarget @('shell','setprop','persist.vendor.adb.enable','0') -AllowFailure | Out-Null
}
[pscustomobject]@{Success=$true;Serial=$identity.Serial;Target=$stableTarget;Report=(Resolve-Path $reportPath).Path}
