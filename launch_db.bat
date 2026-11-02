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

echo [INFO] Launching Database CLI (ChatDemo)...
echo Commands: /send ^<text^>, /recv ^<text^>, /log [n], /count, /clear, /quit
echo ------------------------------------------------------------
java -cp "%JAR%" chat.ChatDemo
pause
