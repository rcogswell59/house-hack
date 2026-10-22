# Local dev helper (Windows PowerShell 5.1). Usage:
#   .\scripts\dev.ps1            -> set JDK 21, ensure databases, build + test
#   .\scripts\dev.ps1 -Run web   -> also start one app: web | collector | analyzer
param(
    [ValidateSet("", "web", "collector", "analyzer")] [string] $Run = ""
)

$ErrorActionPreference = "Stop"

$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Where-Object Name -like "jdk-21*" | Select-Object -First 1
if (-not $jdk) { throw "Temurin JDK 21 not found under C:\Program Files\Eclipse Adoptium. Install: winget install EclipseAdoptium.Temurin.21.JDK" }
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Write-Host "JAVA_HOME = $env:JAVA_HOME"

$psql = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
$createdb = "C:\Program Files\PostgreSQL\16\bin\createdb.exe"
$env:PGPASSWORD = "househack"
foreach ($db in @("househack", "househack_test")) {
    $exists = & $psql -U househack -h localhost -d postgres -tAc "select 1 from pg_database where datname='$db'"
    if ($exists -ne "1") { & $createdb -U househack -h localhost $db; Write-Host "created database $db" }
}

if (-not $env:DATABASE_URL) { $env:DATABASE_URL = "jdbc:postgresql://localhost:5432/househack?user=househack&password=househack" }
if (-not $env:TEST_DATABASE_URL) { $env:TEST_DATABASE_URL = "jdbc:postgresql://localhost:5432/househack_test?user=househack&password=househack" }

Set-Location (Join-Path $PSScriptRoot "..")
& .\gradlew.bat build
if ($LASTEXITCODE -ne 0) { throw "build failed" }

switch ($Run) {
    "web"       { $env:PORT = "8888"; java -jar applications\basic-server\build\libs\basic-server.jar }
    "collector" { $env:PORT = "8886"; java -jar applications\data-collector-server\build\libs\data-collector-server.jar }
    "analyzer"  { $env:PORT = "8887"; java -jar applications\data-analyzer-server\build\libs\data-analyzer-server.jar }
    default     { Write-Host "Build OK. Start apps with: .\scripts\dev.ps1 -Run web (or collector / analyzer)" }
}
