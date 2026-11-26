@echo off
setlocal
title SkuaCraft Build

where gradle >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Gradle 8.8 or newer was not found on PATH.
    echo Install Gradle 8.8 and Java 17, then run this file again.
    pause
    exit /b 1
)

echo [SKUA] Building Minecraft 1.20.1 / Forge 47.4.28...
gradle build
if errorlevel 1 (
    echo.
    echo [FAILED] Check the build output above.
    pause
    exit /b %errorlevel%
)

echo.
echo [SUCCESS] Mod built:
dir /b build\libs\*.jar
pause
