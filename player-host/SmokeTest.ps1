$ErrorActionPreference = "Stop"

$publish = Join-Path $PSScriptRoot "publish/win-x64"
$executable = Join-Path $publish "SkuaCraft.PlayerHost.exe"
$session = Join-Path $env:TEMP "skuacraft-ci-session.json"
$stop = Join-Path $env:TEMP "skuacraft-ci-stop.request"
$stdout = Join-Path $env:TEMP "skuacraft-ci-host.stdout.log"
$stderr = Join-Path $env:TEMP "skuacraft-ci-host.stderr.log"

Remove-Item $session, $stop, $stdout, $stderr -Force -ErrorAction SilentlyContinue
$arguments = '--session-file "{0}" --stop-file "{1}"' -f $session, $stop
$process = Start-Process -FilePath $executable -ArgumentList $arguments -PassThru -NoNewWindow -RedirectStandardOutput $stdout -RedirectStandardError $stderr

try {
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline -and -not (Test-Path $session)) {
        if ($process.HasExited) {
            Get-Content $stdout, $stderr -ErrorAction SilentlyContinue
            throw "Skua player host exited during startup with code $($process.ExitCode)."
        }
        Start-Sleep -Seconds 1
    }

    if (-not (Test-Path $session)) {
        Get-Content $stdout, $stderr -ErrorAction SilentlyContinue
        throw "Skua player host did not publish its session file."
    }

    $state = Get-Content $session -Raw | ConvertFrom-Json
    if ($state.pageUrl -notmatch '^http://127\.0\.0\.1:\d+/') {
        throw "Ruffle page URL is not loopback."
    }
    if ($state.controlUrl -notmatch '^http://127\.0\.0\.1:\d+/api/$') {
        throw "Skua control URL is not loopback."
    }
    if ([string]::IsNullOrWhiteSpace($state.controlToken)) {
        throw "Skua control token is missing."
    }

    $headers = @{ "X-SkuaCraft-Token" = $state.controlToken }
    $scriptList = Invoke-RestMethod -Uri ($state.controlUrl + "scripts") -Headers $headers -Method Get -TimeoutSec 20
    if (-not $scriptList.success -or $scriptList.count -lt 1000) {
        throw "The fork script catalogue did not load. Count: $($scriptList.count)"
    }

    $status = Invoke-RestMethod -Uri ($state.controlUrl + "status") -Headers $headers -Method Get -TimeoutSec 20
    if (-not $status.success) {
        throw "Skua status endpoint returned an unsuccessful response."
    }

    Write-Host "Skua player host started successfully."
    Write-Host "Script catalogue entries: $($scriptList.count)"
    Write-Host "Player URL: $($state.pageUrl)"
}
finally {
    if (-not $process.HasExited) {
        Set-Content -Path $stop -Value "stop"
        try {
            Wait-Process -Id $process.Id -Timeout 20 -ErrorAction Stop
        }
        catch {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }

    $process.Refresh()
    if ($process.HasExited -and $process.ExitCode -ne 0) {
        Get-Content $stdout, $stderr -ErrorAction SilentlyContinue
        throw "Skua player host failed during shutdown with code $($process.ExitCode)."
    }

    Remove-Item $session, $stop -Force -ErrorAction SilentlyContinue
}
