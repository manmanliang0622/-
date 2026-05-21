$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$outDir      = Join-Path $projectRoot "out"
$libDir      = Join-Path $projectRoot "lib"

& (Join-Path $projectRoot "build_javafx.ps1")

$javafxJars = Get-ChildItem -Path $libDir -Filter *.jar | ForEach-Object { $_.FullName }
if (-not $javafxJars) { throw "缺少 ./lib 中的 JavaFX JAR 檔。" }

$modulePath = ($javafxJars -join ";")
java --module-path $modulePath --add-modules javafx.controls -cp $outDir catcatch.CatCatchApp
