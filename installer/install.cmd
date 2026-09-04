@echo off
setlocal
if /I "%YIMO_INSTALL_NO_LAUNCH%"=="1" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -NoLaunch
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
)
if errorlevel 1 pause
exit /b %errorlevel%
