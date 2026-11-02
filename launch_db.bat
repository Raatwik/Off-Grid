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
echo [INFO] Launching Database CLI (ChatDemo)...
echo Commands: /send ^<text^>, /recv ^<text^>, /log [n], /count, /clear, /quit
echo ------------------------------------------------------------
java -cp "%JAR%" chat.ChatDemo
pause
