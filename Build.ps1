[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$GradleVersion = "8.8"
$DistributionName = "gradle-$GradleVersion"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Add-UniquePath {
    param(
        [System.Collections.Generic.List[string]]$Paths,
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return
    }

    $ExpandedPath = [Environment]::ExpandEnvironmentVariables($Path.Trim().Trim('"'))
    if (-not [string]::IsNullOrWhiteSpace($ExpandedPath) -and -not $Paths.Contains($ExpandedPath)) {
        $Paths.Add($ExpandedPath)
    }
}

function Get-Java17Home {
    $Candidates = [System.Collections.Generic.List[string]]::new()

    Add-UniquePath $Candidates $env:JAVA_HOME
    Add-UniquePath $Candidates $env:JDK_HOME

    foreach ($Command in @(Get-Command java.exe -All -ErrorAction SilentlyContinue)) {
        if ($Command.Source) {
            Add-UniquePath $Candidates (Split-Path -Parent (Split-Path -Parent $Command.Source))
        }
    }

    $RegistryRoots = @(
        "HKLM:\SOFTWARE\JavaSoft\JDK",
        "HKLM:\SOFTWARE\JavaSoft\Java Development Kit",
        "HKLM:\SOFTWARE\WOW6432Node\JavaSoft\JDK",
        "HKLM:\SOFTWARE\Eclipse Adoptium\JDK",
        "HKLM:\SOFTWARE\Microsoft\JDK"
    )

    foreach ($RegistryRoot in $RegistryRoots) {
        if (-not (Test-Path $RegistryRoot)) {
            continue
        }

        foreach ($Key in @(Get-ChildItem $RegistryRoot -ErrorAction SilentlyContinue)) {
            $Properties = Get-ItemProperty $Key.PSPath -ErrorAction SilentlyContinue
            if ($Properties.JavaHome) {
                Add-UniquePath $Candidates $Properties.JavaHome
            }
        }
    }

    $SearchRoots = @(
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "Microsoft"),
        (Join-Path $env:ProgramFiles "Amazon Corretto"),
        (Join-Path $env:ProgramFiles "Zulu"),
        (Join-Path $env:ProgramFiles "BellSoft"),
        (Join-Path $env:ProgramFiles "Semeru"),
        (Join-Path $env:ProgramFiles "OpenJDK"),
        (Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium"),
        (Join-Path $env:LOCALAPPDATA "Programs\Microsoft")
    )

    foreach ($SearchRoot in $SearchRoots) {
        if (-not (Test-Path $SearchRoot)) {
            continue
        }

        foreach ($Directory in @(Get-ChildItem $SearchRoot -Directory -ErrorAction SilentlyContinue)) {
            if ($Directory.Name -match "(?i)(jdk|temurin|corretto|zulu|liberica|semeru).*(17)|(17).*(jdk|temurin|corretto|zulu|liberica|semeru)") {
                Add-UniquePath $Candidates $Directory.FullName
            }
        }
    }

    foreach ($Candidate in $Candidates) {
        $JavaExe = Join-Path $Candidate "bin\java.exe"
        $JavacExe = Join-Path $Candidate "bin\javac.exe"

        if (-not (Test-Path $JavaExe) -or -not (Test-Path $JavacExe)) {
            continue
        }

        $VersionOutput = (& $JavaExe -version 2>&1 | Out-String)
        $VersionMatch = [regex]::Match($VersionOutput, '(?i)(?:version\s+"|openjdk\s+)(?<version>\d+(?:\.\d+)*)')

        if (-not $VersionMatch.Success) {
            continue
        }

        $MajorVersion = [int]($VersionMatch.Groups["version"].Value.Split(".")[0])
        if ($MajorVersion -eq 17) {
            return (Resolve-Path $Candidate).Path
        }
    }

    return $null
}

function Get-Gradle88 {
    $Candidates = [System.Collections.Generic.List[string]]::new()

    if ($env:GRADLE_HOME) {
        Add-UniquePath $Candidates (Join-Path $env:GRADLE_HOME "bin\gradle.bat")
    }

    foreach ($Command in @(Get-Command gradle -All -ErrorAction SilentlyContinue)) {
        if ($Command.Source) {
            Add-UniquePath $Candidates $Command.Source
        }
    }

    $KnownPaths = @(
        "C:\Gradle\gradle-$GradleVersion\bin\gradle.bat",
        (Join-Path $env:ProgramFiles "Gradle\gradle-$GradleVersion\bin\gradle.bat"),
        (Join-Path $env:LOCALAPPDATA "Programs\Gradle\gradle-$GradleVersion\bin\gradle.bat"),
        (Join-Path $env:USERPROFILE "scoop\apps\gradle\current\bin\gradle.bat"),
        "C:\tools\gradle-$GradleVersion\bin\gradle.bat"
    )

    foreach ($KnownPath in $KnownPaths) {
        Add-UniquePath $Candidates $KnownPath
    }

    $WrapperCache = Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-$GradleVersion-bin"
    if (Test-Path $WrapperCache) {
        foreach ($GradleBatch in @(Get-ChildItem $WrapperCache -Filter gradle.bat -File -Recurse -ErrorAction SilentlyContinue)) {
            Add-UniquePath $Candidates $GradleBatch.FullName
        }
    }

    foreach ($Candidate in $Candidates) {
        if (-not (Test-Path $Candidate)) {
            continue
        }

        $VersionOutput = (& $Candidate --version 2>&1 | Out-String)
        if ($VersionOutput -match "(?m)^Gradle\s+$([regex]::Escape($GradleVersion))\s*$") {
            return (Resolve-Path $Candidate).Path
        }
    }

    return $null
}

function Install-Gradle88 {
    $ToolsDirectory = Join-Path $env:LOCALAPPDATA "SkuaCraft\tools"
    $InstallDirectory = Join-Path $ToolsDirectory $DistributionName
    $GradleBatch = Join-Path $InstallDirectory "bin\gradle.bat"

    if (Test-Path $GradleBatch) {
        return $GradleBatch
    }

    New-Item -ItemType Directory -Path $ToolsDirectory -Force | Out-Null

    $ArchivePath = Join-Path $ToolsDirectory "$DistributionName-bin.zip"
    $DistributionUrl = "https://services.gradle.org/distributions/$DistributionName-bin.zip"

    Write-Host "[SKUA] Gradle $GradleVersion was not found in the installed locations." -ForegroundColor Yellow
    Write-Host "[SKUA] Downloading the official Gradle $GradleVersion distribution..."

    Invoke-WebRequest -Uri $DistributionUrl -OutFile $ArchivePath -UseBasicParsing
    $ExpectedHash = (Invoke-RestMethod -Uri "$DistributionUrl.sha256").ToString().Trim().Split()[0].ToLowerInvariant()
    $ActualHash = (Get-FileHash -Path $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()

    if ($ActualHash -ne $ExpectedHash) {
        Remove-Item $ArchivePath -Force -ErrorAction SilentlyContinue
        throw "Gradle $GradleVersion download failed SHA-256 verification."
    }

    Expand-Archive -Path $ArchivePath -DestinationPath $ToolsDirectory -Force
    Remove-Item $ArchivePath -Force -ErrorAction SilentlyContinue

    if (-not (Test-Path $GradleBatch)) {
        throw "Gradle $GradleVersion was extracted, but gradle.bat was not found at $GradleBatch."
    }

    return $GradleBatch
}

Write-Host ""
Write-Host "==============================================" -ForegroundColor DarkCyan
Write-Host "             SKUA CRAFT BUILD" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor DarkCyan
Write-Host "Project: $ProjectRoot"
Write-Host "Target:  Minecraft 1.20.1 / Forge 47.4.28"
Write-Host ""

$JavaHome = Get-Java17Home
if (-not $JavaHome) {
    throw "A JDK 17 installation could not be located. Checked JAVA_HOME, JDK_HOME, PATH, Java registry entries, and common Adoptium/Microsoft/Corretto/Zulu install folders. Install a JDK 17 (not just a JRE), then rerun Build.bat."
}

$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome "bin") + [IO.Path]::PathSeparator + $env:PATH

$JavaVersion = (& (Join-Path $JavaHome "bin\java.exe") -version 2>&1 | Select-Object -First 1).ToString()
Write-Host "[OK] Java:   $JavaHome" -ForegroundColor Green
Write-Host "     $JavaVersion"

$GradlePath = Get-Gradle88
if (-not $GradlePath) {
    $GradlePath = Install-Gradle88
}

Write-Host "[OK] Gradle: $GradlePath" -ForegroundColor Green
Write-Host ""
Write-Host "[SKUA] Running Gradle build..." -ForegroundColor Cyan

Push-Location $ProjectRoot
try {
    & $GradlePath build --no-daemon
    $BuildExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($BuildExitCode -ne 0) {
    throw "Gradle build failed with exit code $BuildExitCode."
}

$Artifacts = @(Get-ChildItem (Join-Path $ProjectRoot "build\libs") -Filter "*.jar" -File -ErrorAction SilentlyContinue)
if ($Artifacts.Count -eq 0) {
    throw "Gradle completed, but no mod JAR was found in build\libs."
}

Write-Host ""
Write-Host "[SUCCESS] SkuaCraft build completed." -ForegroundColor Green
Write-Host "Output:"
foreach ($Artifact in $Artifacts) {
    Write-Host "  $($Artifact.FullName)" -ForegroundColor Green
}
