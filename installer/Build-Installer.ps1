$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ReleaseDirectory = Join-Path $ProjectRoot "release"
$PlayerHostDirectory = Join-Path $ProjectRoot "player-host\publish\win-x64"
$GradleFile = Join-Path $ProjectRoot "build.gradle"
$ModOutputDirectory = Join-Path $ProjectRoot "build\libs"
$RinkuCacheDirectory = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\curse.maven\rinku-1366156\8745377"

function Get-InnoSetupCompiler {
    $Candidates = @(
        $env:INNO_SETUP_PATH,
        (Join-Path ${env:ProgramFiles(x86)} "Inno Setup 7\ISCC.exe"),
        (Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\ISCC.exe"),
        (Join-Path $env:ProgramFiles "Inno Setup 7\ISCC.exe"),
        (Join-Path $env:ProgramFiles "Inno Setup 6\ISCC.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 7\ISCC.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe")
    )

    $Command = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if (($null -ne $Command) -and $Command.Source) {
        $Candidates += $Command.Source
    }

    foreach ($Candidate in $Candidates) {
        if (-not [string]::IsNullOrWhiteSpace($Candidate) -and
            (Test-Path -LiteralPath $Candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $Candidate).Path
        }
    }

    return $null
}

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

$Compiler = Get-InnoSetupCompiler
if (-not $Compiler) {
    $Winget = Get-Command winget.exe -ErrorAction SilentlyContinue
    if ($Winget) {
        Write-Host "[SKUA] Inno Setup 6 was not found. Installing the compiler for the current user..." -ForegroundColor Yellow
        & $Winget.Source install --id JRSoftware.InnoSetup --exact --silent --scope user --accept-package-agreements --accept-source-agreements
        $WingetExitCode = $LASTEXITCODE
        $Compiler = Get-InnoSetupCompiler

        if (-not $Compiler) {
            throw "Inno Setup could not be located after winget installation (exit code $WingetExitCode). Install it manually from https://jrsoftware.org/isdl.php, then rerun Build.bat."
        }
    }
}

if (-not $Compiler) {
    throw "Inno Setup 6 is required to create the Windows installer. Install it from https://jrsoftware.org/isdl.php, then rerun Build.bat."
}

New-Item -ItemType Directory -Path $ReleaseDirectory -Force | Out-Null
Copy-Item -LiteralPath $ModJars[0].FullName -Destination (Join-Path $ReleaseDirectory "SkuaCraft-$Version.jar") -Force
Copy-Item -LiteralPath $RinkuJars[0].FullName -Destination (Join-Path $ReleaseDirectory "Rinku-3.0.4.jar") -Force

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
