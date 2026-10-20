param(
    [string]$Destination = (Join-Path $PSScriptRoot '..\Hemregistret-Windows-v0.2')
)
$ErrorActionPreference = 'Stop'
$Destination = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
Push-Location $PSScriptRoot
try {
    if (-not $env:JAVA_HOME) { throw 'Ange JAVA_HOME till ett JDK version 21 eller senare.' }
    & "$PSScriptRoot\mvnw.cmd" -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven-bygget misslyckades.' }
    $runtimePath = Join-Path $Destination 'runtime'
    if (Test-Path -LiteralPath $runtimePath) { throw "Målmappen finns redan: $runtimePath. Välj en ny Destination." }
    & "$env:JAVA_HOME\bin\jlink.exe" `
        --add-modules 'java.desktop,java.sql,java.logging,java.naming,jdk.unsupported,jdk.crypto.ec,jdk.charsets' `
        --strip-debug --no-header-files --no-man-pages --output $runtimePath
    if ($LASTEXITCODE -ne 0) { throw 'Paketeringen misslyckades.' }
    New-Item -ItemType Directory -Force -Path (Join-Path $Destination 'app') | Out-Null
    Copy-Item -Path 'target/package/*.jar' -Destination (Join-Path $Destination 'app')
    Copy-Item -LiteralPath 'packaging/Starta Hemregistret.cmd','packaging/Las mig.txt' -Destination $Destination
    Write-Host "Klart! Dubbelklicka på $Destination\Starta Hemregistret.cmd"
} finally {
    Pop-Location
}
