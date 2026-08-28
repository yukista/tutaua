[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Firmware,

    [Parameter(Mandatory = $false)]
    [string]$PyAmlBootDirectory = "tools\pyamlboot"
)

$ErrorActionPreference = "Stop"
$firmwarePath = (Resolve-Path -LiteralPath $Firmware).Path
$toolPath = (Resolve-Path -LiteralPath $PyAmlBootDirectory).Path
$packer = Join-Path $toolPath "aml_image_packer.py"

if (-not (Test-Path -LiteralPath $packer -PathType Leaf)) {
    throw "No s'ha trobat l'inspector: $packer"
}

$file = Get-Item -LiteralPath $firmwarePath
$hash = Get-FileHash -LiteralPath $firmwarePath -Algorithm SHA256
$stream = [System.IO.File]::OpenRead($firmwarePath)
try {
    $header = [byte[]]::new(64)
    $read = $stream.Read($header, 0, $header.Length)
    if ($read -ne $header.Length) {
        throw "El fitxer és massa curt per contenir una capçalera Amlogic."
    }
} finally {
    $stream.Dispose()
}
$magic = [BitConverter]::ToUInt32($header, 8)
$version = [BitConverter]::ToUInt32($header, 4)
$declaredSize = [BitConverter]::ToUInt64($header, 12)
$itemCount = [BitConverter]::ToUInt32($header, 24)

[pscustomobject]@{
    File = $file.FullName
    Bytes = $file.Length
    SHA256 = $hash.Hash
    Magic = ('0x{0:X8}' -f $magic)
    FormatVersion = $version
    DeclaredBytes = $declaredSize
    ItemCount = $itemCount
} | Format-List

if ($magic -ne 0x27B51956) {
    throw "El fitxer no té la capçalera d'un Amlogic Upgrade Package conegut."
}
if ($declaredSize -ne $file.Length) {
    throw "La mida declarada ($declaredSize) no coincideix amb la mida real ($($file.Length))."
}

Write-Host "Inventari del contenidor (només lectura):"
& python $packer $firmwarePath
if ($LASTEXITCODE -ne 0) {
    throw "L'inspector Amlogic ha retornat el codi $LASTEXITCODE."
}
