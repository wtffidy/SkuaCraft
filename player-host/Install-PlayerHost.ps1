param(
    [string]$InstancePath,
    [switch]$SkipPublish
)

$ErrorActionPreference = "Stop"

$ScriptDirectory = $PSScriptRoot
$ProjectRoot = Split-Path $ScriptDirectory -Parent
$PublishedOutputDirectory = Join-Path $ScriptDirectory "publish\win-x64"
$PublishDirectory = if (Test-Path (Join-Path $ScriptDirectory "SkuaCraft.PlayerHost.exe")) {
    $ScriptDirectory
} else {
    $PublishedOutputDirectory
}
$PlayerHostExecutable = Join-Path $PublishDirectory "SkuaCraft.PlayerHost.exe"

if ([string]::IsNullOrWhiteSpace($InstancePath)) {
    $InstancePath = Read-Host "Paste the Minecraft instance folder path (the folder containing mods and/or config)"
}

$InstancePath = [Environment]::ExpandEnvironmentVariables($InstancePath.Trim().Trim('"').Trim("'"))
if ([string]::IsNullOrWhiteSpace($InstancePath)) {
    throw "No Minecraft instance folder was provided."
}

$InstancePath = [IO.Path]::GetFullPath($InstancePath)
if (-not (Test-Path -LiteralPath $InstancePath -PathType Container)) {
    throw "Minecraft instance folder does not exist: $InstancePath"
}

if (-not (Test-Path (Join-Path $InstancePath "mods")) -and
    -not (Test-Path (Join-Path $InstancePath "config"))) {
    throw "The selected folder does not look like a Minecraft instance (mods/config folder not found): $InstancePath"
}

if (-not (Test-Path -LiteralPath $PlayerHostExecutable -PathType Leaf)) {
    if ($SkipPublish) {
        throw "PlayerHost publish output is missing: $PlayerHostExecutable"
    }

    $ProjectFile = Join-Path $ScriptDirectory "SkuaCraft.PlayerHost.csproj"
    $MvsSource = Join-Path $ProjectRoot "vendor\MVS\src"
    if (-not (Test-Path $MvsSource)) {
        throw "PlayerHost files are not beside this installer, and MVS source is missing. Extract the PlayerHost release ZIP or run git submodule update --init --recursive."
    }

    if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
        throw ".NET 10 SDK is required to publish PlayerHost. Install it from https://dotnet.microsoft.com/download/dotnet/10.0."
    }

    Write-Host "[SKUA] Publishing self-contained Windows PlayerHost..." -ForegroundColor Cyan
    & dotnet publish $ProjectFile -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:PublishTrimmed=false -o $PublishedOutputDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "PlayerHost publish failed with exit code $LASTEXITCODE."
    }
}

if (-not (Test-Path -LiteralPath $PlayerHostExecutable -PathType Leaf)) {
    throw "PlayerHost executable was not found after publishing: $PlayerHostExecutable"
}

$Destination = Join-Path $InstancePath "config\skuacraft\player-host"
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Copy-Item -Path (Join-Path $PublishDirectory "*") -Destination $Destination -Recurse -Force

$InstalledExecutable = Join-Path $Destination "SkuaCraft.PlayerHost.exe"
if (-not (Test-Path -LiteralPath $InstalledExecutable -PathType Leaf)) {
    throw "PlayerHost installation did not produce the expected executable: $InstalledExecutable"
}

Write-Host ""
Write-Host "[SUCCESS] SkuaCraft PlayerHost installed." -ForegroundColor Green
Write-Host "Instance: $InstancePath"
Write-Host "Installed: $InstalledExecutable"
Write-Host "Reopen Minecraft and launch the Skua Console."
