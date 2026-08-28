[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$Adb = "sdk\platform-tools\adb.exe",

    [Parameter(Mandatory = $false)]
    [string]$OutputRoot = "captures\tanix-baseline"
)

$ErrorActionPreference = "Stop"
$adbPath = (Resolve-Path -LiteralPath $Adb).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$output = Join-Path $OutputRoot $stamp
New-Item -ItemType Directory -Force -Path $output | Out-Null

function Invoke-AdbText {
    param([string]$Name, [string[]]$Arguments)
    $path = Join-Path $output $Name
    $result = & $adbPath @Arguments 2>&1
    $result | Set-Content -LiteralPath $path -Encoding utf8
    if ($LASTEXITCODE -ne 0) {
        throw "ADB ha fallat ($LASTEXITCODE): adb $($Arguments -join ' ')"
    }
}

$devices = & $adbPath devices
if (-not ($devices -match "\sdevice$")) {
    throw "No hi ha cap dispositiu ADB autoritzat."
}

& $adbPath root | Out-Host
& $adbPath wait-for-device
if ($LASTEXITCODE -ne 0) {
    throw "No s'ha pogut recuperar ADB després d'activar adbd root."
}

Invoke-AdbText "getprop.txt" @("shell", "getprop")
Invoke-AdbText "proc-partitions.txt" @("shell", "cat /proc/partitions")
Invoke-AdbText "proc-meminfo.txt" @("shell", "cat /proc/meminfo")
Invoke-AdbText "mounts.txt" @("shell", "cat /proc/mounts")
Invoke-AdbText "block-by-name.txt" @("shell", 'find /dev/block -type l -path "*by-name*" -exec ls -l {} \;')
Invoke-AdbText "block-sizes.txt" @("shell", 'for p in /dev/block/by-name/*; do echo "$p $(blockdev --getsize64 "$p" 2>/dev/null)"; done')
Invoke-AdbText "bootctl.txt" @("shell", "bootctl 2>&1 || bootctl-hal-info 2>&1 || true")
Invoke-AdbText "lpdump.txt" @("shell", "lpdump 2>&1 || true")
Invoke-AdbText "device-policy.txt" @("shell", "dumpsys device_policy")
Invoke-AdbText "packages.txt" @("shell", "pm list packages -f -U -u")
Invoke-AdbText "features.txt" @("shell", "pm list features")
Invoke-AdbText "update-engine.txt" @("shell", "update_engine_client --status 2>&1 || dumpsys update_engine 2>&1 || true")
Invoke-AdbText "avb.txt" @("shell", "avbctl get-verity 2>&1; avbctl get-verification 2>&1; getprop ro.boot.vbmeta.device_state; getprop ro.boot.verifiedbootstate")
Invoke-AdbText "network.txt" @("shell", "ip addr; ip route; getprop | grep -E 'wifi|wlan|bluetooth|btmodule'")
Invoke-AdbText "hardware-files.txt" @("shell", 'for f in /sys/block/mmcblk0/device/name /sys/block/mmcblk0/device/cid /sys/block/mmcblk0/device/csd /sys/block/mmcblk0/device/manfid /sys/block/mmcblk0/device/oemid /sys/block/mmcblk0/device/serial /sys/block/mmcblk0/device/date; do echo "=== $f"; cat "$f" 2>/dev/null; done')

$remoteHead = "/data/local/tmp/tutaua-gpt-head.bin"
$remoteTail = "/data/local/tmp/tutaua-gpt-tail.bin"
$remoteBoot0 = "/data/local/tmp/tutaua-mmc-boot0.bin"
$remoteBoot1 = "/data/local/tmp/tutaua-mmc-boot1.bin"
try {
    $sectorText = (& $adbPath shell "blockdev --getsz /dev/block/mmcblk0").Trim()
    if ($LASTEXITCODE -ne 0 -or $sectorText -notmatch '^\d+$') {
        throw "No s'ha pogut determinar la mida de l'eMMC."
    }
    [Int64]$sectors = $sectorText
    [Int64]$count = 16384
    if ($sectors -le $count) { throw "La mida de l'eMMC no és vàlida." }
    [Int64]$tailStart = $sectors - $count

    & $adbPath shell "dd if=/dev/block/mmcblk0 of=$remoteHead bs=512 count=$count"
    if ($LASTEXITCODE -ne 0) { throw "No s'han pogut llegir els sectors inicials." }
    & $adbPath shell "dd if=/dev/block/mmcblk0 of=$remoteTail bs=512 skip=$tailStart count=$count"
    if ($LASTEXITCODE -ne 0) { throw "No s'han pogut llegir els sectors finals." }
    & $adbPath shell "dd if=/dev/block/mmcblk0boot0 of=$remoteBoot0 bs=1M"
    if ($LASTEXITCODE -ne 0) { throw "No s'ha pogut llegir mmcblk0boot0." }
    & $adbPath shell "dd if=/dev/block/mmcblk0boot1 of=$remoteBoot1 bs=1M"
    if ($LASTEXITCODE -ne 0) { throw "No s'ha pogut llegir mmcblk0boot1." }
    & $adbPath pull $remoteHead (Join-Path $output "mmc-gpt-head.bin")
    if ($LASTEXITCODE -ne 0) { throw "No s'ha pogut copiar la GPT primària." }
    & $adbPath pull $remoteTail (Join-Path $output "mmc-gpt-tail.bin")
    if ($LASTEXITCODE -ne 0) { throw "No s'ha pogut copiar la GPT secundària." }
    & $adbPath pull $remoteBoot0 (Join-Path $output "mmc-boot0.bin")
    if ($LASTEXITCODE -ne 0) { throw "No s'ha pogut copiar mmcblk0boot0." }
    & $adbPath pull $remoteBoot1 (Join-Path $output "mmc-boot1.bin")
    if ($LASTEXITCODE -ne 0) { throw "No s'ha pogut copiar mmcblk0boot1." }
} finally {
    & $adbPath shell "rm -f $remoteHead $remoteTail $remoteBoot0 $remoteBoot1" | Out-Null
}

Get-ChildItem -LiteralPath $output -File |
    Get-FileHash -Algorithm SHA256 |
    ForEach-Object { "$($_.Hash)  $(Split-Path $_.Path -Leaf)" } |
    Set-Content -LiteralPath (Join-Path $output "SHA256SUMS.txt") -Encoding ascii

Write-Host "Captura completada: $((Resolve-Path -LiteralPath $output).Path)"
