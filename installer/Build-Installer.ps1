$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ReleaseDirectory = Join-Path $ProjectRoot "release"
$PlayerHostDirectory = Join-Path $ProjectRoot "player-host\publish\win-x64"
$GradleFile = Join-Path $ProjectRoot "build.gradle"
$ModOutputDirectory = Join-Path $ProjectRoot "build\libs"
$RinkuCacheDirectory = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\curse.maven\rinku-1366156\8745377"

$GradleText = Get-Content -LiteralPath $GradleFile -Raw
$VersionMatch = [regex]::Match($GradleText, "(?m)^version\s*=\s*'([^']+)'")
if (-not $VersionMatch.Success) {
    throw "Could not read the project version from build.gradle."
}

$Version = $VersionMatch.Groups[1].Value
$ModJars = @(Get-ChildItem -LiteralPath $ModOutputDirectory -Filter "*.jar" -File | Where-Object { $_.Name -notmatch '(-sources|-javadoc)\.jar$' })
if ($ModJars.Count -ne 1) {
    throw "Expected exactly one installable SkuaCraft JAR, found $($ModJars.Count)."
}

if (-not (Test-Path -LiteralPath (Join-Path $PlayerHostDirectory "SkuaCraft.PlayerHost.exe") -PathType Leaf)) {
    throw "PlayerHost publish output is missing. Build the PlayerHost before packaging the installer."
}

$RinkuJars = @(Get-ChildItem -LiteralPath $RinkuCacheDirectory -Filter "*.jar" -File -Recurse -ErrorAction SilentlyContinue)
if ($RinkuJars.Count -eq 0) {
    throw "Rinku 3.0.4 was not found in the Gradle CurseMaven cache at '$RinkuCacheDirectory'."
}
if ($RinkuJars.Count -gt 1) {
    $RinkuJars = @($RinkuJars | Sort-Object LastWriteTimeUtc -Descending)
}

New-Item -ItemType Directory -Path $ReleaseDirectory -Force | Out-Null
Copy-Item -LiteralPath $ModJars[0].FullName -Destination (Join-Path $ReleaseDirectory "SkuaCraft-$Version.jar") -Force
Copy-Item -LiteralPath $RinkuJars[0].FullName -Destination (Join-Path $ReleaseDirectory "Rinku-3.0.4.jar") -Force

$CompilerCandidates = @(
    (Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\ISCC.exe"),
    (Join-Path $env:ProgramFiles "Inno Setup 6\ISCC.exe")
)
$Compiler = $CompilerCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if (-not $Compiler) {
    throw "Inno Setup 6 compiler was not found. Install it with: choco install innosetup --no-progress -y"
}

$InstallerScript = Join-Path $PSScriptRoot "SkuaCraft.iss"
& $Compiler "/DAppVersion=$Version" $InstallerScript
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup compilation failed with exit code $LASTEXITCODE."
}

$InstallerPath = Join-Path $ReleaseDirectory "SkuaCraft-Setup-$Version.exe"
if (-not (Test-Path -LiteralPath $InstallerPath -PathType Leaf)) {
    throw "Installer compilation completed without producing '$InstallerPath'."
}

Write-Host "[SUCCESS] Created $InstallerPath" -ForegroundColor Green
