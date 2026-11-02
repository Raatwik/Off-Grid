@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "JAR=target\chat-app.jar"

where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java is not installed or not in PATH. Please install Java 17+ and try again.
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo [INFO] %JAR% not found. Attempting to build with Maven...
    where mvn >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        call mvn package -DskipTests
    ) else (
        echo [ERROR] %JAR% not found and Maven (mvn) is not in PATH.
        echo Please build the project first using 'mvn package' or your IDE.
        pause
        exit /b 1
    )
)

set "PORT=%~1"
set "BAUD=%~2"
if "%BAUD%"=="" set "BAUD=115200"

if "%PORT%"=="" (
    echo Available COM Ports:
    powershell -NoProfile -Command "[System.IO.Ports.SerialPort]::GetPortNames() | ForEach-Object { Write-Host '  - ' $_ }" 2>nul
    set /p "PORT=Enter COM port [default COM3]: "
)
if "%PORT%"=="" set "PORT=COM3"

echo [INFO] Launching Off-Grid Campus Chat GUI on %PORT% @ %BAUD% baud...
java -cp "%JAR%" chat.ChatFrontend "%PORT%" %BAUD%
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Application exited with error code %ERRORLEVEL%.
    pause
)
