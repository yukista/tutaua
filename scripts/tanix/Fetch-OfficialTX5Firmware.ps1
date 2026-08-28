[CmdletBinding()]
param(
    [string]$DestinationDirectory = (Join-Path $PSScriptRoot '..\..\downloads\tanix'),
    [string]$GoogleDriveFileId = '17kjabRCmTr4rbrl810qY5fGPYbJtQomF'
)

$ErrorActionPreference = 'Stop'

if ($GoogleDriveFileId -notmatch '^[A-Za-z0-9_-]{20,}$') {
    throw 'Identificador de Google Drive no vàlid.'
}

$destination = [System.IO.Path]::GetFullPath($DestinationDirectory)
New-Item -ItemType Directory -Path $destination -Force | Out-Null

$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    throw "No s'ha trobat Python al PATH."
}

& $python.Source -c 'import gdown' 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "Falta el mòdul gdown. Instal·la'l en un entorn temporal abans de repetir la descàrrega."
}

$url = "https://drive.google.com/uc?id=$GoogleDriveFileId"
Write-Host "Font oficial: https://www.tanixtvbox.com/firmware-centre/"
Write-Host "Destinació: $destination"

& $python.Source -m gdown $url --continue --output ($destination + [IO.Path]::DirectorySeparatorChar)
if ($LASTEXITCODE -ne 0) {
    throw "Google Drive no ha completat la descàrrega (pot ser quota excedida). No s'ha validat cap firmware."
}

$files = Get-ChildItem -LiteralPath $destination -File |
    Where-Object { $_.Name -notlike '*.part' } |
    Sort-Object LastWriteTimeUtc -Descending
if (-not $files) {
    throw 'La descàrrega no ha produït cap fitxer.'
}

$files | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
    [pscustomobject]@{
        Path = $_.FullName
        Bytes = $_.Length
        SHA256 = $hash.Hash
    }
} | Format-Table -AutoSize

Write-Warning 'Descàrrega completada, però encara NO està autoritzada per flashejar. Executa Inspect-AmlogicFirmware.ps1 sobre el contenidor corresponent.'
