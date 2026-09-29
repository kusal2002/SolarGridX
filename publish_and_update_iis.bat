@echo off
echo ========================================================
echo   Updating SolarGridX Backend in IIS (C:\inetpub\SolarGridX)
echo ========================================================
echo.
echo 1. Building and publishing latest backend code...
cd /d "f:\SLIIT\SLIIT\SolarGridX\SolarGridX"
dotnet publish -c Release -o "bin\Release\net10.0\publish"
if %errorlevel% neq 0 (
    echo [ERROR] Dotnet build failed!
    pause
    exit /b %errorlevel%
)

echo.
echo 2. Updating files in C:\inetpub\SolarGridX and recycling IIS...
powershell -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command Copy-Item -Path \"f:\SLIIT\SLIIT\SolarGridX\SolarGridX\bin\Release\net10.0\publish\*\" -Destination \"C:\inetpub\SolarGridX\" -Recurse -Force; Stop-Process -Name w3wp -Force -ErrorAction SilentlyContinue; Write-Host \"[SUCCESS] Backend files updated and IIS refreshed!\" -ForegroundColor Green; Start-Sleep -Seconds 2'"

echo.
echo [DONE] Successfully completed!
