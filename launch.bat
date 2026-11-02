@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

set "ARG1=%~1"
if /i "%ARG1%"=="app" goto :launch_app
if /i "%ARG1%"=="gui" goto :launch_app
if /i "%ARG1%"=="db" goto :launch_db
if /i "%ARG1%"=="build" goto :rebuild

:menu
cls
echo ========================================================
echo             Off-Grid Campus Chat Launcher
echo ========================================================
echo  1. Launch Chat App (Swing GUI)
echo  2. Launch Database CLI (ChatDemo)
echo  3. Rebuild chat-app.jar (Maven)
echo  4. Exit
echo ========================================================
set "CHOICE="
set /p "CHOICE=Enter choice [1-4]: "

if "%CHOICE%"=="1" goto :launch_app
if "%CHOICE%"=="2" goto :launch_db
if "%CHOICE%"=="3" goto :rebuild
if "%CHOICE%"=="4" exit /b 0

echo Invalid choice. Try again.
pause
goto :menu

:launch_app
call "%SCRIPT_DIR%launch_app.bat" %2 %3
exit /b %ERRORLEVEL%

:launch_db
call "%SCRIPT_DIR%launch_db.bat"
exit /b %ERRORLEVEL%

:rebuild
echo [INFO] Rebuilding target\chat-app.jar...
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven (mvn) is not in PATH.
    echo Please install Maven or run 'package' inside your IDE (e.g. IntelliJ / Eclipse).
    pause
    goto :menu
)
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Build failed.
) else (
    echo [SUCCESS] Rebuilt target\chat-app.jar with latest code!
)
pause
goto :menu
