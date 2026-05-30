param([int]$Port = 5050)
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$outDir      = Join-Path $projectRoot "out"

& (Join-Path $projectRoot "build_javafx.ps1")

Write-Host "Starting CatCatch server on port $Port ..."
java -cp $outDir catcatch.GameServer $Port
