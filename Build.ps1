[CmdletBinding()]
param(
    [switch]$SkipFolderPicker
)

$ErrorActionPreference = "Stop"
$GradleVersion = "8.8"
$JdkVersion = "17.0.21.1+2"
$JdkDistributionName = "jdk-$JdkVersion"
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
        (Join-Path $env:LOCALAPPDATA "Programs\Microsoft"),
        (Join-Path $env:LOCALAPPDATA "SkuaCraft\tools")
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

        # java.exe writes its version banner to stderr. Read both streams
        # through Process directly so PowerShell's Stop preference cannot
        # turn that normal output into a NativeCommandError.
        $VersionStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $VersionStartInfo.FileName = $JavaExe
        $VersionStartInfo.Arguments = "-version"
        $VersionStartInfo.UseShellExecute = $false
        $VersionStartInfo.CreateNoWindow = $true
        $VersionStartInfo.RedirectStandardOutput = $true
        $VersionStartInfo.RedirectStandardError = $true

        $VersionProcess = [System.Diagnostics.Process]::new()
        $VersionProcess.StartInfo = $VersionStartInfo

        try {
            [void]$VersionProcess.Start()
            $VersionStdOut = $VersionProcess.StandardOutput.ReadToEnd()
            $VersionStdErr = $VersionProcess.StandardError.ReadToEnd()
            $VersionProcess.WaitForExit()

            if ($VersionProcess.ExitCode -ne 0) {
                continue
            }

            $VersionOutput = $VersionStdOut + [Environment]::NewLine + $VersionStdErr
        }
        finally {
            $VersionProcess.Dispose()
        }

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

function Install-Java17 {
    $ToolsDirectory = Join-Path $env:LOCALAPPDATA "SkuaCraft\tools"
    $InstallDirectory = Join-Path $ToolsDirectory $JdkDistributionName
    $JavaExe = Join-Path $InstallDirectory "bin\java.exe"
    $JavacExe = Join-Path $InstallDirectory "bin\javac.exe"

    if ((Test-Path $JavaExe) -and (Test-Path $JavacExe)) {
        return $InstallDirectory
    }

    New-Item -ItemType Directory -Path $ToolsDirectory -Force | Out-Null

    $ArchiveName = "OpenJDK17U-jdk_x64_windows_hotspot_17.0.21.1_2.zip"
    $ArchivePath = Join-Path $ToolsDirectory $ArchiveName
    $StagingDirectory = Join-Path $ToolsDirectory "$JdkDistributionName-extract"
    $DistributionUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21.1%2B2/$ArchiveName"

    Write-Host "[SKUA] JDK 17 was not found. Downloading Eclipse Temurin JDK $JdkVersion..." -ForegroundColor Yellow
    Write-Host "[SKUA] This is a one-time download and will not replace your existing Java installation."

    Invoke-WebRequest -Uri $DistributionUrl -OutFile $ArchivePath -UseBasicParsing

    # SHA-256 digest published for this exact asset by the official Adoptium release.
    $ExpectedHash = "eafba0944f777abdc74d5aad16049c9b5a1275b6ce99e2939ba9f7f3e6f08c87"
    $ActualHash = (Get-FileHash -Path $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()

    if ($ActualHash -ne $ExpectedHash) {
        Remove-Item $ArchivePath -Force -ErrorAction SilentlyContinue
        throw "Temurin JDK $JdkVersion download failed SHA-256 verification."
    }

    if (Test-Path $StagingDirectory) {
        Remove-Item $StagingDirectory -Recurse -Force
    }

    New-Item -ItemType Directory -Path $StagingDirectory -Force | Out-Null

    try {
        Expand-Archive -Path $ArchivePath -DestinationPath $StagingDirectory -Force

        $ExtractedHome = Get-ChildItem $StagingDirectory -Directory |
            Where-Object {
                (Test-Path (Join-Path $_.FullName "bin\java.exe")) -and
                (Test-Path (Join-Path $_.FullName "bin\javac.exe"))
            } |
            Select-Object -First 1

        if (-not $ExtractedHome) {
            throw "The Temurin archive was verified but did not contain a usable Windows JDK."
        }

        if (Test-Path $InstallDirectory) {
            Remove-Item $InstallDirectory -Recurse -Force
        }

        Move-Item -LiteralPath $ExtractedHome.FullName -Destination $InstallDirectory
    }
    finally {
        Remove-Item $StagingDirectory -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item $ArchivePath -Force -ErrorAction SilentlyContinue
    }

    if (-not (Test-Path $JavaExe) -or -not (Test-Path $JavacExe)) {
        throw "Temurin JDK $JdkVersion was extracted, but java.exe or javac.exe is missing."
    }

    Write-Host "[OK] Downloaded and verified JDK 17." -ForegroundColor Green
    return $InstallDirectory
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
    # Official Gradle 8.8 binary distribution checksum:
    # https://gradle.org/release-checksums/
    $ExpectedHash = "a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612"
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
    $JavaHome = Install-Java17
}

$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome "bin") + [IO.Path]::PathSeparator + $env:PATH

$JavaVersionStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
$JavaVersionStartInfo.FileName = Join-Path $JavaHome "bin\java.exe"
$JavaVersionStartInfo.Arguments = "-version"
$JavaVersionStartInfo.UseShellExecute = $false
$JavaVersionStartInfo.CreateNoWindow = $true
$JavaVersionStartInfo.RedirectStandardOutput = $true
$JavaVersionStartInfo.RedirectStandardError = $true
$JavaVersionProcess = [System.Diagnostics.Process]::new()
$JavaVersionProcess.StartInfo = $JavaVersionStartInfo

try {
    [void]$JavaVersionProcess.Start()
    $JavaVersionStdOut = $JavaVersionProcess.StandardOutput.ReadToEnd()
    $JavaVersionStdErr = $JavaVersionProcess.StandardError.ReadToEnd()
    $JavaVersionProcess.WaitForExit()
    $JavaVersion = ($JavaVersionStdOut + $JavaVersionStdErr).Trim() -split "\r?\n" | Select-Object -First 1
}
finally {
    $JavaVersionProcess.Dispose()
}

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

$DistributionDirectory = Join-Path $ProjectRoot "build"
New-Item -ItemType Directory -Path $DistributionDirectory -Force | Out-Null

# Remove old copied JARs so the build folder always represents the latest successful build.
Get-ChildItem $DistributionDirectory -Filter "*.jar" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force

foreach ($Artifact in $Artifacts) {
    $Destination = Join-Path $DistributionDirectory $Artifact.Name
    Copy-Item -LiteralPath $Artifact.FullName -Destination $Destination -Force
}

Write-Host ""
Write-Host "[SUCCESS] SkuaCraft build completed." -ForegroundColor Green
Write-Host "Build output:"
foreach ($Artifact in $Artifacts) {
    Write-Host "  $($Artifact.FullName)" -ForegroundColor DarkGray
}
Write-Host "Ready-to-install JAR:"
Get-ChildItem $DistributionDirectory -Filter "*.jar" -File |
    ForEach-Object { Write-Host "  $($_.FullName)" -ForegroundColor Green }
if (-not $SkipFolderPicker) {
    Write-Host ""
    $SelectFolder = Read-Host "Select another folder for the built JAR? (Y/N)"

    if ($SelectFolder -match '^(?i)y(es)?$') {
        try {
            Add-Type -AssemblyName System.Windows.Forms
            $FolderDialog = [System.Windows.Forms.FolderBrowserDialog]::new()
            $FolderDialog.Description = "Choose where to copy the SkuaCraft JAR"
            $FolderDialog.ShowNewFolderButton = $true

            if ($FolderDialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK -and
                -not [string]::IsNullOrWhiteSpace($FolderDialog.SelectedPath)) {
                foreach ($Artifact in @(Get-ChildItem $DistributionDirectory -Filter "*.jar" -File)) {
                    $SelectedDestination = Join-Path $FolderDialog.SelectedPath $Artifact.Name
                    Copy-Item -LiteralPath $Artifact.FullName -Destination $SelectedDestination -Force
                    Write-Host "[OK] Copied to: $SelectedDestination" -ForegroundColor Green
                }
            }
            else {
                Write-Host "[INFO] Folder selection cancelled. JAR remains in the build folder." -ForegroundColor Yellow
            }
        }
        finally {
            if ($FolderDialog) {
                $FolderDialog.Dispose()
            }
        }
    }
}
