@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "JAR=%SCRIPT_DIR%chat-app.jar"
if not exist "%JAR%" set "JAR=%SCRIPT_DIR%target\chat-app.jar"

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java is not installed or not in PATH. Please install Java 17+ and try again.
    pause
    exit /b 1
)

if exist "%JAR%" goto :jar_ready

echo [INFO] chat-app.jar not found. Attempting to build with Maven...
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] chat-app.jar not found and Maven (mvn) is not in PATH.
    echo Please build the project first using 'mvn package' or your IDE.
    pause
    exit /b 1
)
call mvn package -DskipTests
if errorlevel 1 (
    echo [ERROR] Maven build failed.
    pause
    exit /b 1
)
if exist "%SCRIPT_DIR%target\chat-app.jar" set "JAR=%SCRIPT_DIR%target\chat-app.jar"

:jar_ready
set "PORT=%~1"
set "BAUD=%~2"
if "%BAUD%"=="" set "BAUD=115200"

if not "%PORT%"=="" goto :got_port

echo Available COM Ports:
powershell -NoProfile -Command "[System.IO.Ports.SerialPort]::GetPortNames()" 2>nul
set /p "PORT=Enter COM port [default: COM3]: "

:got_port
if "%PORT%"=="" set "PORT=COM3"

echo [INFO] Launching Off-Grid Campus Chat GUI on %PORT% @ %BAUD% baud...
java -cp "%JAR%" chat.ChatFrontend "%PORT%" %BAUD%
if errorlevel 1 (
    echo [ERROR] Application exited with error.
    pause
)
