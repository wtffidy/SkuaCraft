@echo off
setlocal
title SkuaCraft Build, PlayerHost and Installer

set "SCRIPT_DIR=%~dp0"
set "BUILD_SCRIPT=%SCRIPT_DIR%Build.ps1"
set "INSTALLER_SCRIPT=%SCRIPT_DIR%installer\Build-Installer.ps1"

if not exist "%BUILD_SCRIPT%" (
    echo [ERROR] Build.ps1 was not found.
    echo Expected: "%BUILD_SCRIPT%"
    pause
    exit /b 1
)

if not exist "%INSTALLER_SCRIPT%" (
    echo [ERROR] Installer build script was not found.
    echo Expected: "%INSTALLER_SCRIPT%"
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

if not "%BUILD_EXIT%"=="0" (
    goto build_failed
)

echo.
echo [SKUA] Building the guided Windows installer...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%INSTALLER_SCRIPT%"
set "BUILD_EXIT=%ERRORLEVEL%"

if not "%BUILD_EXIT%"=="0" (
    goto build_failed
)

echo.
echo [SUCCESS] SkuaCraft mod, PlayerHost and Windows installer build completed.
echo Installer output: "%SCRIPT_DIR%release"
goto finish

:build_failed
echo.
echo [FAILED] Build exited with code %BUILD_EXIT%.

:finish
pause
exit /b %BUILD_EXIT%
