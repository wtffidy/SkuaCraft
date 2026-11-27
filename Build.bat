@echo off
setlocal
title SkuaCraft Build and PlayerHost

set "SCRIPT_DIR=%~dp0"
set "BUILD_SCRIPT=%SCRIPT_DIR%Build.ps1"

if not exist "%BUILD_SCRIPT%" (
    echo [ERROR] Build.ps1 was not found.
    echo Expected: "%BUILD_SCRIPT%"
    pause
    exit /b 1
)

where powershell.exe >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Windows PowerShell was not found.
    pause
    exit /b 1
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%BUILD_SCRIPT%"
set "BUILD_EXIT=%ERRORLEVEL%"

echo.
if "%BUILD_EXIT%"=="0" (
    echo [SUCCESS] SkuaCraft mod and PlayerHost build completed.
) else (
    echo [FAILED] Build exited with code %BUILD_EXIT%.
)

pause
exit /b %BUILD_EXIT%
