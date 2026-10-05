@echo off
echo Run this file as Administrator to update the local SolarGridX IIS backend.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Update-LocalIIS.ps1"
if errorlevel 1 echo Backend update failed. Review the error above.
pause
