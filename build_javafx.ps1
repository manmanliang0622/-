$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcDir = Join-Path $projectRoot "src"
$outDir = Join-Path $projectRoot "out"
$libDir = Join-Path $projectRoot "lib"

if (Test-Path -LiteralPath $outDir) {
    Remove-Item -LiteralPath $outDir -Recurse -Force
}
New-Item -ItemType Directory -Path $outDir | Out-Null

$javafxJars = Get-ChildItem -Path $libDir -Filter *.jar | ForEach-Object { $_.FullName }
if (-not $javafxJars) {
    throw "Missing JavaFX jars in ./lib. Please commit the lib folder to GitHub."
}

$modulePath = ($javafxJars -join ";")
$javaFiles = Get-ChildItem -Path $srcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }

javac -encoding UTF-8 --module-path $modulePath --add-modules javafx.controls -d $outDir $javaFiles
Write-Host "Build complete: $outDir"
