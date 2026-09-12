[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$TutauaApk,
    [Parameter(Mandatory = $true)][string]$BoxApk,
    [Parameter(Mandatory = $true)][string]$TvApk,
    [Parameter(Mandatory = $true)][string]$TdtApk,
    [Parameter(Mandatory = $true)][string]$GamesApk,
    [Parameter(Mandatory = $true)][string]$ManagerApk,
    [Parameter(Mandatory = $true)][uri]$JellyfinServer,
    [Parameter(Mandatory = $true)][uri]$TvApiUrl,
    [uri]$TvStreamUrl,
    [uri]$UpdateManifestUrl,
    [ValidatePattern('^[A-Za-z0-9._-]{1,32}$')][string]$UpdateChannel = 'stable',
    [Parameter(Mandatory = $true)][System.Management.Automation.PSCredential]$Credential,
    [Parameter(Mandatory = $true)][uri]$ControlServer,
    [Parameter(Mandatory = $true)][string]$ControlLanAddress,
    [Parameter(Mandatory = $true)][System.Security.SecureString]$ProvisioningApiKey,
    [string]$Adb = 'sdk\platform-tools\adb.exe',
    [string]$ReportDirectory = 'provisioning-reports',
    [string]$SystemApkBackupDirectory = 'device-backups',
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
$tdtApkPath = (Resolve-Path -LiteralPath $TdtApk).Path
$gamesApkPath = (Resolve-Path -LiteralPath $GamesApk).Path
$managerApkPath = (Resolve-Path -LiteralPath $ManagerApk).Path
$managerPermissionsPath = (Resolve-Path -LiteralPath 'scripts\tanix\privapp-permissions-tutaua-manager.xml').Path
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
$augustSystemApks = [ordered]@{
    '/system/priv-app/ConfigUpdater/ConfigUpdater.apk' = 'com.google.android.configupdater'
    '/system/priv-app/GoogleBackupTransport/GoogleBackupTransport.apk' = 'com.google.android.backuptransport'
    '/system/priv-app/Katniss/Katniss.apk' = 'com.google.android.katniss'
    '/system/priv-app/AtvRemoteService/AtvRemoteService.apk' = 'com.google.android.tv.remote.service'
    '/system/priv-app/PrebuiltGmsCorePano/PrebuiltGmsCorePano.apk' = 'com.google.android.gms'
    '/system/priv-app/GoogleServicesFramework/GoogleServicesFramework.apk' = 'com.google.android.gsf'
}

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
function Test-RemoteFile([string]$Serial, [string]$Path) {
    Invoke-Adb $Serial @('shell','test','-f',$Path) -AllowFailure | Out-Null
    return $LASTEXITCODE -eq 0
}
function Wait-ForAndroid([string]$Serial, [int]$Seconds = 120) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $state = ''
        try {
            & $adbPath connect $Serial 2>$null | Out-Null
            $state = (& $adbPath -s $Serial get-state 2>$null | Select-Object -First 1)
            if ($state -eq 'device' -and (Get-Prop $Serial 'sys.boot_completed') -eq '1') { return }
        } catch {
            # 'offline' and a missing transport are expected while adbd restarts.
        }
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

# Wireless debugging uses an ephemeral TLS port. Move immediately to the
# firmware's stable laboratory port because restarting adbd invalidates the
# original transport.
if ($Target -ne $stableTarget -or (Get-Prop $Target 'persist.vendor.adb.enable') -ne '1') {
    Invoke-Adb $Target @('shell','setprop','persist.vendor.adb.enable','1') | Out-Null
    Wait-ForAndroid $stableTarget
}
$Target = $stableTarget

# The August firmware restores these six Google packages to enabled at every
# boot. On this exact build, use reversible overlayfs renames so PackageManager
# cannot register them. Original files are pulled before the first change.
if ($identity.Build -eq 'eng.fjq.20250814.110011') {
    $pendingSystemApks = @($augustSystemApks.Keys | Where-Object {
        Test-RemoteFile $Target $_
    })
    foreach ($source in $augustSystemApks.Keys) {
        $disabledPath = "$source.tutaua-disabled"
        if ((Test-RemoteFile $Target $source) -and (Test-RemoteFile $Target $disabledPath)) {
            throw "Estat ambigu: existeixen tant $source com $disabledPath."
        }
    }
    if ($pendingSystemApks.Count -gt 0) {
        $safeBuild = $identity.Build -replace '[^A-Za-z0-9_.-]','_'
        $systemBackupPath = Join-Path $SystemApkBackupDirectory "$($identity.Serial)-$safeBuild-system-google"
        New-Item -ItemType Directory -Path $systemBackupPath -Force | Out-Null
        foreach ($source in $pendingSystemApks) {
            $remoteDirectory = $source -replace '/[^/]+$',''
            & $adbPath -s $Target pull $remoteDirectory $systemBackupPath | Out-Host
            if ($LASTEXITCODE -ne 0) { throw "No s'ha pogut copiar $remoteDirectory." }
        }
        & $adbPath -s $Target root | Out-Host
        & $adbPath -s $Target remount | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'La ROM no permet preparar overlayfs.' }
        Invoke-Adb $Target @('reboot') | Out-Null
        Wait-ForAndroid $stableTarget 150
        & $adbPath -s $stableTarget remount | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut muntar overlayfs en escriptura.' }
        foreach ($source in $pendingSystemApks) {
            Invoke-Adb $stableTarget @('shell','mv',$source,"$source.tutaua-disabled") | Out-Host
        }
        Invoke-Adb $stableTarget @('shell','sync') | Out-Null
        Invoke-Adb $stableTarget @('reboot') | Out-Null
        Wait-ForAndroid $stableTarget 150
        foreach ($source in $pendingSystemApks) {
            if (Test-RemoteFile $stableTarget $source) { throw "La retirada no ha persistit: $source" }
        }
        $Target = $stableTarget
    }
    if (-not (Test-RemoteFile $Target '/system/priv-app/TutauaManager/TutauaManager.apk')) {
        & $adbPath -s $Target remount | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut muntar overlayfs per instal lar Manager.' }
        Invoke-Adb $Target @('shell','mkdir','-p','/system/priv-app/TutauaManager') | Out-Null
        & $adbPath -s $Target push $managerApkPath '/system/priv-app/TutauaManager/TutauaManager.apk' | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut copiar Tutaua Manager al sistema.' }
        & $adbPath -s $Target push $managerPermissionsPath '/system/etc/permissions/privapp-permissions-tutaua-manager.xml' | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut copiar l allowlist de Tutaua Manager.' }
        Invoke-Adb $Target @('shell','chmod','0644','/system/priv-app/TutauaManager/TutauaManager.apk') | Out-Null
        Invoke-Adb $Target @('shell','chmod','0644','/system/etc/permissions/privapp-permissions-tutaua-manager.xml') | Out-Null
        Invoke-Adb $Target @('shell','sync') | Out-Null
        Invoke-Adb $Target @('reboot') | Out-Null
        Wait-ForAndroid $stableTarget 150
        $Target = $stableTarget
    }
}

foreach ($item in @(
    @{Name='Tutaua Box';Path=$boxApkPath},
    @{Name='Tutaua';Path=$tutauaApkPath},
    @{Name='Tutaua TV';Path=$tvApkPath},
    @{Name='Tutaua TDT';Path=$tdtApkPath},
    @{Name='Tutaua Retro';Path=$gamesApkPath},
    @{Name='Tutaua Manager';Path=$managerApkPath}
)) {
    & $adbPath -s $Target install -r -i com.yukista.tutaua.manager $item.Path | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "La instal·lació de $($item.Name) ha fallat." }
}
$managerPackage = (Invoke-Adb $Target @('shell','dumpsys','package','com.yukista.tutaua.manager') -AllowFailure) -join "`n"
if ($managerPackage -notmatch 'android\.permission\.INSTALL_PACKAGES:\s+granted=true') {
    throw 'Tutaua Manager no te el permis privilegiat INSTALL_PACKAGES.'
}
$deviceName = "TX5-$($identity.Serial)" -replace '[^A-Za-z0-9._-]','_'
$enrollmentUri = $ControlServer.AbsoluteUri.TrimEnd('/') + '/v1/provisioning/enrollment-codes'
$enrollmentBody = @{deviceName=$deviceName;lifetimeMinutes=30} | ConvertTo-Json -Compress
$provisioningSecret = [System.Net.NetworkCredential]::new('', $ProvisioningApiKey).Password
try {
    $controlPort = if ($ControlServer.IsDefaultPort) { 443 } else { $ControlServer.Port }
    $resolve = "$($ControlServer.Host):${controlPort}:$ControlLanAddress"
    $enrollmentJson = $enrollmentBody | & curl.exe --silent --show-error --fail --resolve $resolve `
        -H "Authorization: Bearer $provisioningSecret" -H 'Content-Type: application/json' `
        --data-binary '@-' $enrollmentUri
    if ($LASTEXITCODE -ne 0) { throw 'Tutaua Control ha rebutjat la creacio del codi d enrolament.' }
    $enrollment = $enrollmentJson | ConvertFrom-Json
} finally { $provisioningSecret = $null }
if ([string]::IsNullOrWhiteSpace($enrollment.code)) { throw 'Tutaua Control no ha retornat un codi d enrolament.' }
Invoke-Adb $Target @('logcat','-c') | Out-Null
Invoke-Adb $Target @('shell','am','broadcast','-a','com.yukista.tutaua.manager.action.PROVISION',
    '-n','com.yukista.tutaua.manager/.ProvisioningReceiver',
    '--es','server_b64',(ConvertTo-Base64 $ControlServer.AbsoluteUri.TrimEnd('/')),
    '--es','lan_address_b64',(ConvertTo-Base64 $ControlLanAddress),
    '--es','code_b64',(ConvertTo-Base64 $enrollment.code)) | Out-Null
$managerDeadline = (Get-Date).AddSeconds(45); $managerResult = ''
do {
    Start-Sleep -Seconds 2
    $managerResult = (Invoke-Adb $Target @('logcat','-d','-s','TUTAUA_MANAGER:I','*:S')) -join "`n"
} while ($managerResult -notmatch 'enrollment (SUCCESS|ALREADY_ENROLLED)' -and (Get-Date) -lt $managerDeadline)
if ($managerResult -notmatch 'enrollment (SUCCESS|ALREADY_ENROLLED)') { throw 'Tutaua Manager no s ha pogut enrolar.' }
Invoke-Adb $Target @('shell','pm','grant','com.yukista.tutaua.games','android.permission.READ_EXTERNAL_STORAGE') | Out-Null
Invoke-Adb $Target @('shell','settings','put','secure','enabled_accessibility_services','com.yukista.tutaua.box/.RemoteKeyService') | Out-Null
Invoke-Adb $Target @('shell','settings','put','secure','accessibility_enabled','1') | Out-Null
$boxRequestId = [guid]::NewGuid().ToString('N')
Invoke-Adb $Target @('logcat','-c') | Out-Null
Invoke-Adb $Target @('shell','am','broadcast','-a','com.yukista.tutaua.box.action.PROVISION',
    '-n','com.yukista.tutaua.box/.ProvisioningReceiver','--es','request_id',$boxRequestId,
    '--es','fleet_b64',(ConvertTo-Base64 $ControlServer.AbsoluteUri.TrimEnd('/')),
    '--es','fleet_lan_b64',(ConvertTo-Base64 $ControlLanAddress),
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
$applicablePackages = @($packages | Where-Object { $installed -contains "package:$_" })
$absentPackages = @($packages | Where-Object { $installed -notcontains "package:$_" })
$disabledBefore = (Invoke-Adb $Target @('shell','pm','list','packages','-d','--user','0'))
foreach ($package in $applicablePackages) {
    if ($disabledBefore -notcontains "package:$package") {
        Invoke-Adb $Target @('shell','pm','disable-user','--user','0',$package) | Out-Host
        # Some firmware builds return a non-zero status when force-stopping a
        # package immediately after disabling it. The disabled state is the
        # required quarantine; force-stop is only a best-effort cleanup.
        Invoke-Adb $Target @('shell','am','force-stop','--user','0',$package) -AllowFailure | Out-Null
    }
}
Invoke-Adb $Target @('shell','settings','put','system','screen_off_timeout',"$screenOffTimeoutMs") | Out-Null
Start-Sleep -Seconds 4
& $adbPath connect $stableTarget | Out-Null
Invoke-Adb $stableTarget @('reboot') | Out-Null
Wait-ForAndroid $stableTarget
Invoke-Adb $stableTarget @('shell','input','keyevent','HOME') | Out-Null
Start-Sleep -Seconds 3

$disabledAfter = Invoke-Adb $stableTarget @('shell','pm','list','packages','-d','--user','0')
$missingDisabled = @($applicablePackages | Where-Object { $disabledAfter -notcontains "package:$_" })
$resolvedHome = (Invoke-Adb $stableTarget @('shell','cmd','package','resolve-activity','--brief','--user','0','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')) -join "`n"
$settings = (Invoke-Adb $stableTarget @('shell','cmd','package','resolve-activity','--brief','-a','android.settings.SETTINGS')) -join "`n"
$webView = (Invoke-Adb $stableTarget @('shell','dumpsys','webviewupdate')) -join "`n"
$network = (Invoke-Adb $stableTarget @('shell','ping','-c','1','-W','4','1.1.1.1') -AllowFailure) -join "`n"
$configuredScreenOffTimeout = [int](
    (Invoke-Adb $stableTarget @('shell','settings','get','system','screen_off_timeout') | Select-Object -First 1)
)
$success = $missingDisabled.Count -eq 0 -and $resolvedHome -match 'com\.yukista\.tutaua\.box' -and
    $settings -match 'com\.android\.tv\.settings' -and $webView -match 'Current WebView package.*com\.android\.webview' -and
    $network -match '1 received' -and $configuredScreenOffTimeout -eq $screenOffTimeoutMs

$report = [ordered]@{Timestamp=(Get-Date).ToString('o');Success=$success;Identity=$identity;
    ExactBuild=$buildMatches;Apks=@(
        @{Package='com.yukista.tutaua.box';Path=$boxApkPath;Sha256=(Get-FileHash -LiteralPath $boxApkPath -Algorithm SHA256).Hash},
        @{Package='com.yukista.tutaua';Path=$tutauaApkPath;Sha256=(Get-FileHash -LiteralPath $tutauaApkPath -Algorithm SHA256).Hash},
        @{Package='tv.tutaua.app';Path=$tvApkPath;Sha256=(Get-FileHash -LiteralPath $tvApkPath -Algorithm SHA256).Hash},
        @{Package='com.yukista.tutaua.games';Path=$gamesApkPath;Sha256=(Get-FileHash -LiteralPath $gamesApkPath -Algorithm SHA256).Hash},
        @{Package='com.yukista.tutaua.manager';Path=$managerApkPath;Sha256=(Get-FileHash -LiteralPath $managerApkPath -Algorithm SHA256).Hash}
    );
    DisabledPackages=$applicablePackages;AbsentPackages=$absentPackages;
    MissingDisabledPackages=$missingDisabled;Home=$resolvedHome;Settings=$settings;
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
