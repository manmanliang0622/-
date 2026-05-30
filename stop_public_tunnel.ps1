$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pidPath = Join-Path $projectRoot "cloudflared.pid"
$urlPath = Join-Path $projectRoot "public-url.txt"

if (-not (Test-Path -LiteralPath $pidPath)) {
    Write-Host "No tunnel pid file found."
    exit 0
}

$pidValue = Get-Content -LiteralPath $pidPath -ErrorAction SilentlyContinue
if ($pidValue) {
    try {
        Stop-Process -Id ([int]$pidValue) -Force -ErrorAction Stop
        Write-Host "Stopped tunnel process $pidValue"
    } catch {
        Write-Host "Tunnel process $pidValue was not running."
    }
}

Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $urlPath -Force -ErrorAction SilentlyContinue
