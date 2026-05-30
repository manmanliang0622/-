$ErrorActionPreference = "Stop"

param(
    [int]$Port = 5050
)

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$logPath = Join-Path $projectRoot "cloudflared.log"
$errPath = Join-Path $projectRoot "cloudflared-error.log"
$pidPath = Join-Path $projectRoot "cloudflared.pid"
$urlPath = Join-Path $projectRoot "public-url.txt"

$candidates = @(
    (Join-Path $projectRoot "tools\\cloudflared.exe"),
    "C:\Users\user\OneDrive - 淡江大學\文件\Playground\cat_catch_project\tools\cloudflared.exe"
)
$cloudflared = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

if (-not $cloudflared) {
    throw "找不到 cloudflared.exe，請先把它放到 tools 資料夾。"
}

if (Test-Path -LiteralPath $pidPath) {
    $oldPid = Get-Content -LiteralPath $pidPath -ErrorAction SilentlyContinue
    if ($oldPid) {
        try {
            Stop-Process -Id ([int]$oldPid) -Force -ErrorAction Stop
        } catch {
        }
    }
}

foreach ($path in @($logPath, $errPath, $urlPath, $pidPath)) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Force
    }
}

$proc = Start-Process -FilePath $cloudflared `
    -ArgumentList @("tunnel", "--url", "http://127.0.0.1:$Port", "--no-autoupdate") `
    -WorkingDirectory $projectRoot `
    -WindowStyle Hidden `
    -RedirectStandardOutput $logPath `
    -RedirectStandardError $errPath `
    -PassThru

Set-Content -LiteralPath $pidPath -Value $proc.Id -Encoding ascii

$url = $null
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 500
    foreach ($candidate in @($logPath, $errPath)) {
        if (Test-Path -LiteralPath $candidate) {
            $match = Select-String -LiteralPath $candidate -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches | Select-Object -Last 1
            if ($match) {
                $url = $match.Matches[-1].Value
                break
            }
        }
    }
    if ($url) {
        break
    }
}

if (-not $url) {
    throw "Tunnel 啟動失敗，請檢查 cloudflared.log 與 cloudflared-error.log。"
}

Set-Content -LiteralPath $urlPath -Value $url -Encoding ascii
Write-Host "Public URL: $url"
