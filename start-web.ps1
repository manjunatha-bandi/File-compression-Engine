Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  COMPRESSION ENGINE v0.1 - WEB DASHBOARD" -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Cyan

if (-not (Test-Path "java/target/classes")) {
    New-Item -ItemType Directory -Path "java/target/classes" -Force | Out-Null
}

Write-Host "Compiling Java sources..." -ForegroundColor Yellow
$sources = (Get-ChildItem -Recurse -Filter *.java java/src/main).FullName
javac -d java/target/classes $sources

Write-Host "Starting Web Server on http://localhost:8080..." -ForegroundColor Green
Start-Process "http://localhost:8080"
java -cp java/target/classes com.compression.cli.Main server 8080
