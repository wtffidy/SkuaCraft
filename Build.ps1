[CmdletBinding()]
param(
    [Alias("SkipFolderPicker")]
    [switch]$SkipPrompts
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

function Get-LastUsedDirectory {
    param([string]$SettingName)

    $SettingsDirectory = Join-Path $env:LOCALAPPDATA "SkuaCraft"
    $SettingsFile = Join-Path $SettingsDirectory "$SettingName.txt"
    if (-not (Test-Path -LiteralPath $SettingsFile -PathType Leaf)) {
        return $null
    }

    $SavedPath = (Get-Content -LiteralPath $SettingsFile -Raw -ErrorAction SilentlyContinue).Trim()
    if ([string]::IsNullOrWhiteSpace($SavedPath) -or -not (Test-Path -LiteralPath $SavedPath -PathType Container)) {
        return $null
    }

    return [IO.Path]::GetFullPath($SavedPath)
}

function Save-LastUsedDirectory {
    param([string]$SettingName, [string]$Path)

    try {
        $SettingsDirectory = Join-Path $env:LOCALAPPDATA "SkuaCraft"
        New-Item -ItemType Directory -Path $SettingsDirectory -Force | Out-Null
        $SettingsFile = Join-Path $SettingsDirectory "$SettingName.txt"
        [IO.File]::WriteAllText($SettingsFile, $Path)
    }
    catch {
        Write-Warning "Could not save the last-used folder: $($_.Exception.Message)"
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

# Sync the fork before any build tools are selected or started.
$GitExe = Get-Command git.exe -ErrorAction SilentlyContinue
if (-not $GitExe) {
    throw "Git for Windows is required. Install Git, then rerun Build.bat."
}

$GitRoot = (& git -C $ProjectRoot rev-parse --show-toplevel 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($GitRoot)) {
    throw "This build folder is not a Git checkout. Clone https://github.com/wtffidy/SkuaCraft and run Build.bat from that folder."
}

$GitRoot = [IO.Path]::GetFullPath($GitRoot)
if ($GitRoot -ine [IO.Path]::GetFullPath($ProjectRoot)) {
    throw "Build.ps1 must be run from the SkuaCraft repository root. Git root: $GitRoot"
}

$OriginUrl = (& git -C $ProjectRoot remote get-url origin 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $OriginUrl -notmatch '(?i)github\.com[:/]wtffidy/SkuaCraft(?:\.git)?$') {
    throw "The origin remote must point to https://github.com/wtffidy/SkuaCraft. Current origin: '$OriginUrl'."
}

$IsGitHubActions = $env:GITHUB_ACTIONS -eq "true"
$CurrentBranch = (& git -C $ProjectRoot branch --show-current 2>&1 | Out-String).Trim()
if (-not $IsGitHubActions -and ($LASTEXITCODE -ne 0 -or $CurrentBranch -ne "main")) {
    throw "Builds must run from the main branch. Current branch: '$CurrentBranch'. Switch to main and rerun Build.bat."
}

$WorkingTree = @(& git -C $ProjectRoot status --porcelain 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect the Git working tree: $($WorkingTree -join [Environment]::NewLine)"
}
if ($WorkingTree.Count -gt 0) {
    throw "Your SkuaCraft working tree has local changes. Commit or stash them before building so the automatic update cannot overwrite your work."
}

if ($IsGitHubActions) {
    # Actions checks out the exact commit under test in detached-HEAD mode.
    # Do not pull main here or replace the commit that triggered this workflow.
    Write-Host "[GIT] GitHub Actions detected; using the checked-out commit." -ForegroundColor Cyan
}
else {
    Write-Host "[GIT] Fetching the latest SkuaCraft main..." -ForegroundColor Cyan
    & git -C $ProjectRoot fetch origin main
    if ($LASTEXITCODE -ne 0) {
        throw "Git fetch failed. Check your connection and the origin remote, then rerun Build.bat."
    }

    & git -C $ProjectRoot pull --ff-only origin main
    if ($LASTEXITCODE -ne 0) {
        throw "Git could not fast-forward main. Resolve the local branch state manually, then rerun Build.bat."
    }
}

Write-Host "[GIT] Updating pinned submodules..." -ForegroundColor Cyan
& git -C $ProjectRoot submodule update --init --recursive
if ($LASTEXITCODE -ne 0) {
    throw "Git submodule update failed. Check your connection and rerun Build.bat."
}

$UpdatedCommit = (& git -C $ProjectRoot rev-parse --short HEAD 2>&1 | Out-String).Trim()
Write-Host "[GIT] Building SkuaCraft commit $UpdatedCommit." -ForegroundColor Green

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
# Publish the companion .NET host as part of the normal build.
$PlayerHostPublishScript = Join-Path $ProjectRoot "player-host\Publish-PlayerHost.ps1"
$PlayerHostPublishDirectory = Join-Path $ProjectRoot "player-host\publish\win-x64"
$PlayerHostExecutable = Join-Path $PlayerHostPublishDirectory "SkuaCraft.PlayerHost.exe"

if (-not (Test-Path -LiteralPath $PlayerHostPublishScript -PathType Leaf)) {
    throw "PlayerHost publish script was not found: $PlayerHostPublishScript"
}

if (-not (Get-Command dotnet.exe -ErrorAction SilentlyContinue) -and
    -not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    throw ".NET 10 SDK is required to build PlayerHost. Install it from https://dotnet.microsoft.com/download/dotnet/10.0 and rerun Build.bat."
}

Write-Host ""
Write-Host "[SKUA] Publishing Windows x64 PlayerHost..." -ForegroundColor Cyan
& $PlayerHostPublishScript -Configuration "Release" -Runtime "win-x64"
if ($LASTEXITCODE -ne 0) {
    throw "PlayerHost publish failed with exit code $LASTEXITCODE."
}

if (-not (Test-Path -LiteralPath $PlayerHostExecutable -PathType Leaf)) {
    throw "PlayerHost publish completed without creating: $PlayerHostExecutable"
}

Write-Host "[SUCCESS] PlayerHost published: $PlayerHostExecutable" -ForegroundColor Green

if (-not $SkipPrompts) {
    Write-Host ""
    $CopyJar = Read-Host "Copy the built SkuaCraft JAR to another folder? (Y/N)"

    if ($CopyJar -match '^(?i)y(es)?$') {
        $LastJarDestination = Get-LastUsedDirectory "last-jar-destination"
        if ([string]::IsNullOrWhiteSpace($LastJarDestination)) {
            $JarDestination = Read-Host "Paste the destination folder path (for example, your Minecraft instance's mods folder)"
        }
        else {
            $JarDestination = Read-Host "Destination folder (press Enter to reuse '$LastJarDestination')"
            if ([string]::IsNullOrWhiteSpace($JarDestination)) {
                $JarDestination = $LastJarDestination
            }
        }
        $JarDestination = [Environment]::ExpandEnvironmentVariables($JarDestination.Trim().Trim('"').Trim("'"))

        if ([string]::IsNullOrWhiteSpace($JarDestination)) {
            throw "No JAR destination folder was provided."
        }

        $JarDestination = [IO.Path]::GetFullPath($JarDestination)
        if (-not (Test-Path -LiteralPath $JarDestination -PathType Container)) {
            throw "JAR destination folder does not exist: $JarDestination"
        }

        foreach ($Artifact in @(Get-ChildItem $DistributionDirectory -Filter "*.jar" -File)) {
            $SelectedDestination = Join-Path $JarDestination $Artifact.Name
            Copy-Item -LiteralPath $Artifact.FullName -Destination $SelectedDestination -Force
            Write-Host "[OK] Copied JAR to: $SelectedDestination" -ForegroundColor Green
        }
        Save-LastUsedDirectory -SettingName "last-jar-destination" -Path $JarDestination
    }

    Write-Host ""
    $InstallPlayerHost = Read-Host "Install the PlayerHost into a Minecraft instance now? (Y/N)"

    if ($InstallPlayerHost -match '^(?i)y(es)?$') {
        $LastInstancePath = Get-LastUsedDirectory "last-instance-path"
        if ([string]::IsNullOrWhiteSpace($LastInstancePath)) {
            $InstancePath = Read-Host "Paste the Minecraft instance folder path (the folder containing mods and/or config)"
        }
        else {
            $InstancePath = Read-Host "Minecraft instance folder (press Enter to reuse '$LastInstancePath')"
            if ([string]::IsNullOrWhiteSpace($InstancePath)) {
                $InstancePath = $LastInstancePath
            }
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

        $HostDestination = Join-Path $InstancePath "config\skuacraft\player-host"
        New-Item -ItemType Directory -Path $HostDestination -Force | Out-Null

        # The executable cannot be replaced while this instance is using it.
        $InstalledHostExecutable = Join-Path $HostDestination "SkuaCraft.PlayerHost.exe"
        while ($true) {
            # Match the exact executable path for this instance. Other Minecraft
            # instances can keep their own PlayerHost processes running.
            $RunningHost = @(Get-CimInstance -ClassName Win32_Process -Filter "Name = 'SkuaCraft.PlayerHost.exe'" -ErrorAction SilentlyContinue |
                Where-Object {
                    try {
                        $_.ExecutablePath -and [IO.Path]::GetFullPath($_.ExecutablePath) -ieq [IO.Path]::GetFullPath($InstalledHostExecutable)
                    }
                    catch {
                        $false
                    }
                })

            if ($RunningHost.Count -eq 0) {
                break
            }

            Write-Host ""
            Write-Host "[WAIT] This instance's SkuaCraft.PlayerHost is still running." -ForegroundColor Yellow
            Write-Host "Close the Minecraft instance using this PlayerHost folder, then press Enter to retry. Type N to cancel."
            $RetryInstall = Read-Host "Retry installation"

            if ($RetryInstall -match '^[Nn](o)?

        try {
            Copy-Item -Path (Join-Path $PlayerHostPublishDirectory "*") -Destination $HostDestination -Recurse -Force -ErrorAction Stop
        }
        catch {
            throw "Could not install PlayerHost to '$HostDestination'. Make sure Minecraft and SkuaCraft.PlayerHost are closed, then rerun Build.ps1. Details: $($_.Exception.Message)"
        }

        if (-not (Test-Path -LiteralPath $InstalledHostExecutable -PathType Leaf)) {
            throw "PlayerHost installation did not produce the expected executable in: $HostDestination"
        }

        Write-Host "[SUCCESS] PlayerHost installed: $HostDestination" -ForegroundColor Green
        Save-LastUsedDirectory -SettingName "last-instance-path" -Path $InstancePath
    }
}

Write-Host ""
Write-Host "[SUCCESS] SkuaCraft and PlayerHost build completed." -ForegroundColor Green
Write-Host "Mod JAR output: $DistributionDirectory"
Write-Host "PlayerHost output: $PlayerHostPublishDirectory"
) {
                throw "PlayerHost installation cancelled because this instance's PlayerHost is still running."
            }
        }

        try {
            Copy-Item -Path (Join-Path $PlayerHostPublishDirectory "*") -Destination $HostDestination -Recurse -Force -ErrorAction Stop
        }
        catch {
            throw "Could not install PlayerHost to '$HostDestination'. Make sure Minecraft and SkuaCraft.PlayerHost are closed, then rerun Build.ps1. Details: $($_.Exception.Message)"
        }

        if (-not (Test-Path -LiteralPath $InstalledHostExecutable -PathType Leaf)) {
            throw "PlayerHost installation did not produce the expected executable in: $HostDestination"
        }

        Write-Host "[SUCCESS] PlayerHost installed: $HostDestination" -ForegroundColor Green
        Save-LastUsedDirectory -SettingName "last-instance-path" -Path $InstancePath
    }
}

Write-Host ""
Write-Host "[SUCCESS] SkuaCraft and PlayerHost build completed." -ForegroundColor Green
Write-Host "Mod JAR output: $DistributionDirectory"
Write-Host "PlayerHost output: $PlayerHostPublishDirectory"
