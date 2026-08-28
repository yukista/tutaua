[CmdletBinding()]
param([string]$Adb = 'sdk\platform-tools\adb.exe', [string]$Target = '192.168.1.143:5555')

$adbPath = (Resolve-Path -LiteralPath $Adb).Path
& $adbPath -s $Target shell pm enable --user 0 com.google.android.katniss
if ($LASTEXITCODE -ne 0) { throw 'No s ha pogut restaurar Katniss.' }
