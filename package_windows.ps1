param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type = "app-image",
    [string]$Version = "1.0.0",
    [string]$Vendor = "CatCatch Team",
    [switch]$SkipServer
)

$ErrorActionPreference = "Stop"

function Get-JdkHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\\jpackage.exe"))) {
        return $env:JAVA_HOME
    }

    $jdk = Get-ChildItem "C:\\Program Files\\Java" -Directory -Filter "jdk*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($jdk -and (Test-Path (Join-Path $jdk.FullName "bin\\jpackage.exe"))) {
        return $jdk.FullName
    }

    throw "jpackage was not found. Install JDK 17+ or set JAVA_HOME."
}

function Test-WixInstalled {
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $light = Get-Command light.exe -ErrorAction SilentlyContinue
    return ($null -ne $candle -and $null -ne $light)
}

function Resolve-WixPath {
    param(
        [string]$ProjectRoot
    )

    $localWixDir = Join-Path $ProjectRoot "tools\\wix314"
    $localCandle = Join-Path $localWixDir "candle.exe"
    $localLight = Join-Path $localWixDir "light.exe"
    if ((Test-Path $localCandle) -and (Test-Path $localLight)) {
        return $localWixDir
    }

    if (Test-WixInstalled) {
        return $null
    }

    return $null
}

function New-StageLayout {
    param(
        [string]$BaseRoot,
        [string]$TargetName
    )

    $targetRoot = Join-Path $BaseRoot $TargetName
    if (Test-Path -LiteralPath $targetRoot) {
        Remove-Item -LiteralPath $targetRoot -Recurse -Force
    }

    $layout = @{
        TargetRoot = $targetRoot
        InputDir = Join-Path $targetRoot "input"
        TempDir = Join-Path $targetRoot "temp"
        DistDir = Join-Path $targetRoot "dist"
        LibDir = Join-Path $targetRoot "lib"
        IconsDir = Join-Path $targetRoot "icons"
        JarPath = Join-Path (Join-Path $targetRoot "input") "CatCatch.jar"
        IconPath = Join-Path (Join-Path $targetRoot "icons") "catcatch.ico"
    }

    foreach ($path in $layout.Values) {
        if ($path -is [string] -and ($path.EndsWith("input") -or $path.EndsWith("temp") -or $path.EndsWith("dist") -or $path.EndsWith("lib") -or $path.EndsWith("icons"))) {
            New-Item -ItemType Directory -Path $path -Force | Out-Null
        }
    }

    return $layout
}

function Invoke-MsiFallback {
    param(
        [string]$WixDir,
        [string]$StageTempDir,
        [string]$StageDistDir,
        [string]$PackageName,
        [string]$Version
    )

    $lightExe = if ($WixDir) { Join-Path $WixDir "light.exe" } else { "light.exe" }
    $configDir = Join-Path $StageTempDir "config"
    $wixObjDir = Join-Path $StageTempDir "wixobj"
    $locFile = Join-Path $configDir "MsiInstallerStrings_en.wxl"
    $outputFile = Join-Path $StageDistDir ($PackageName + "-" + $Version + ".msi")
    $wixObjects = Get-ChildItem -Path $wixObjDir -Filter *.wixobj | Sort-Object Name | ForEach-Object { $_.FullName }

    if (-not $wixObjects) {
        throw "No wixobj files were generated for MSI fallback."
    }

    & $lightExe -nologo -spdb -sval -ext WixUtilExtension -ext WixUIExtension `
        -b $configDir `
        -sice:ICE27 `
        -loc $locFile `
        -cultures:en-us `
        -out $outputFile `
        @wixObjects

    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $outputFile)) {
        throw "MSI fallback failed for $PackageName."
    }

    return $outputFile
}

function Package-Target {
    param(
        [string]$PackageName,
        [string]$MainClass,
        [bool]$ConsoleLauncher,
        [hashtable]$Stage,
        [string]$RequestedType,
        [string]$Version,
        [string]$Vendor,
        [string]$JpackageExe,
        [string]$ModulePath,
        [string]$ProjectDistDir,
        [string]$WixDir
    )

    $actualType = $RequestedType
    if (($RequestedType -eq "exe" -or $RequestedType -eq "msi") -and -not $WixDir -and -not (Test-WixInstalled)) {
        throw "WiX is required for installer packaging."
    }

    $commonArgs = @(
        "--type", $RequestedType,
        "--dest", $Stage.DistDir,
        "--temp", $Stage.TempDir,
        "--input", $Stage.InputDir,
        "--main-jar", "CatCatch.jar",
        "--app-version", $Version,
        "--vendor", $Vendor,
        "--copyright", "Copyright (c) 2026 CatCatch Team",
        "--description", "JavaFX multiplayer cat catching game",
        "--icon", $Stage.IconPath
    )

    $targetArgs = @(
        "--name", $PackageName,
        "--main-class", $MainClass,
        "--module-path", $ModulePath,
        "--add-modules", "javafx.controls",
        "--java-options", "-Dfile.encoding=UTF-8"
    )

    if ($ConsoleLauncher) {
        $targetArgs += "--win-console"
    }

    if ($RequestedType -ne "app-image") {
        $targetArgs += @(
            "--win-menu",
            "--win-shortcut",
            "--win-dir-chooser",
            "--win-menu-group", "CatCatch"
        )
    }

    Write-Host "Packaging $PackageName as $RequestedType ..."
    & $JpackageExe @commonArgs @targetArgs
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        if ($RequestedType -eq "app-image") {
            throw "jpackage failed for $PackageName app-image."
        }

        Write-Warning "jpackage $RequestedType failed for $PackageName. Falling back to MSI packaging with ICE validation disabled."
        $msiPath = Invoke-MsiFallback -WixDir $WixDir -StageTempDir $Stage.TempDir -StageDistDir $Stage.DistDir -PackageName $PackageName -Version $Version
        $actualType = "msi"
        Write-Host "Fallback installer created: $msiPath"
    }

    Copy-Item -Path (Join-Path $Stage.DistDir "*") -Destination $ProjectDistDir -Recurse -Force

    return $actualType
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDistDir = Join-Path $projectRoot "dist"
$asciiStageRoot = Join-Path $env:TEMP "catcatch-package"

$jdkHome = Get-JdkHome
$jpackageExe = Join-Path $jdkHome "bin\\jpackage.exe"
$jarExe = Join-Path $jdkHome "bin\\jar.exe"
$wixPath = Resolve-WixPath -ProjectRoot $projectRoot

if ($wixPath) {
    $env:PATH = $wixPath + ";" + $env:PATH
    Write-Host "Using local WiX binaries from $wixPath"
}

& (Join-Path $projectRoot "build_javafx.ps1")

if (Test-Path -LiteralPath $asciiStageRoot) {
    Remove-Item -LiteralPath $asciiStageRoot -Recurse -Force
}
if (Test-Path -LiteralPath $projectDistDir) {
    Remove-Item -LiteralPath $projectDistDir -Recurse -Force
}
New-Item -ItemType Directory -Path $asciiStageRoot -Force | Out-Null
New-Item -ItemType Directory -Path $projectDistDir -Force | Out-Null

$clientStage = New-StageLayout -BaseRoot $asciiStageRoot -TargetName "CatCatch"
Copy-Item -Path (Join-Path $projectRoot "lib\\*.jar") -Destination $clientStage.LibDir -Force
Copy-Item -LiteralPath (Join-Path $projectRoot "packaging\\icons\\catcatch.ico") -Destination $clientStage.IconPath -Force
& $jarExe --create --file $clientStage.JarPath -C (Join-Path $projectRoot "out") .
$modulePath = ($clientStage.LibDir + ";" + (Join-Path $jdkHome "jmods"))

$clientType = Package-Target `
    -PackageName "CatCatch" `
    -MainClass "catcatch.CatCatchJavaFxApp" `
    -ConsoleLauncher:$false `
    -Stage $clientStage `
    -RequestedType $Type `
    -Version $Version `
    -Vendor $Vendor `
    -JpackageExe $jpackageExe `
    -ModulePath $modulePath `
    -ProjectDistDir $projectDistDir `
    -WixDir $wixPath

$serverType = $null
if (-not $SkipServer) {
    $serverStage = New-StageLayout -BaseRoot $asciiStageRoot -TargetName "CatCatchServer"
    Copy-Item -Path (Join-Path $projectRoot "lib\\*.jar") -Destination $serverStage.LibDir -Force
    Copy-Item -LiteralPath (Join-Path $projectRoot "packaging\\icons\\catcatch.ico") -Destination $serverStage.IconPath -Force
    & $jarExe --create --file $serverStage.JarPath -C (Join-Path $projectRoot "out") .
    $serverModulePath = ($serverStage.LibDir + ";" + (Join-Path $jdkHome "jmods"))

    $serverType = Package-Target `
        -PackageName "CatCatchServer" `
        -MainClass "catcatch.CatCatchServer" `
        -ConsoleLauncher:$true `
        -Stage $serverStage `
        -RequestedType $Type `
        -Version $Version `
        -Vendor $Vendor `
        -JpackageExe $jpackageExe `
        -ModulePath $serverModulePath `
        -ProjectDistDir $projectDistDir `
        -WixDir $wixPath
}

Write-Host ""
Write-Host "Package output:"
Write-Host "  $projectDistDir"
if ($clientType -eq "app-image") {
    Write-Host "  $projectDistDir\\CatCatch\\CatCatch.exe"
} else {
    Write-Host "  $projectDistDir\\CatCatch-1.0.0.msi"
}
if (-not $SkipServer) {
    if ($serverType -eq "app-image") {
        Write-Host "  $projectDistDir\\CatCatchServer\\CatCatchServer.exe"
    } else {
        Write-Host "  $projectDistDir\\CatCatchServer-1.0.0.msi"
    }
}
