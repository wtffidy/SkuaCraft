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

# Skua resolves these files from AppContext.BaseDirectory at runtime. Keep
# them beside the single-file host instead of relying on bundle content rules.
$SkuaDataDirectory = Join-Path $MvsSource "Skua.App.Avalonia"
foreach ($DataFile in @("AdvancedSkills.json", "QuestData.json")) {
    $SourceFile = Join-Path $SkuaDataDirectory $DataFile
    $DestinationFile = Join-Path $Output $DataFile

    if (-not (Test-Path -LiteralPath $SourceFile -PathType Leaf)) {
        throw "Required Skua data file is missing from the pinned MVS source: $SourceFile"
    }

    Copy-Item -LiteralPath $SourceFile -Destination $DestinationFile -Force

    if (-not (Test-Path -LiteralPath $DestinationFile -PathType Leaf)) {
        throw "Could not publish the required Skua data file: $DestinationFile"
    }

    Write-Host "[OK] Published Skua data file: $DataFile" -ForegroundColor Green
}

Write-Host ""
Write-Host "Player host published to: $Output" -ForegroundColor Green
Write-Host "Copy the published files to: <Minecraft instance>\config\skuacraft\player-host\"
