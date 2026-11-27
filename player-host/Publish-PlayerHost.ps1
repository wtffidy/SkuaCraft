param(
    [string]$Configuration = "Release",
    [string]$Runtime = "win-x64"
)

$ErrorActionPreference = "Stop"
$Project = Join-Path $PSScriptRoot "SkuaCraft.PlayerHost.csproj"
$Output = Join-Path $PSScriptRoot "publish\$Runtime"

$MvsSource = Join-Path $PSScriptRoot "..\vendor\MVS\src"
if (-not (Test-Path $MvsSource)) {
    throw "MVS source is missing. Run: git submodule update --init --recursive"
}

dotnet publish $Project -c $Configuration -r $Runtime --self-contained true -p:PublishSingleFile=true -p:PublishTrimmed=false -o $Output
if ($LASTEXITCODE -ne 0) {
    throw "SkuaCraft Player Host publish failed with exit code $LASTEXITCODE."
}

Write-Host ""
Write-Host "Player host published to: $Output" -ForegroundColor Green
Write-Host "Copy the published files to: <Minecraft instance>\config\skuacraft\player-host\"
