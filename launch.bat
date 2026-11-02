@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

if /i "%~1"=="app" goto :launch_app
if /i "%~1"=="gui" goto :launch_app
if /i "%~1"=="db" goto :launch_db

:menu
cls
echo ========================================================
echo             Off-Grid Campus Chat Launcher
echo ========================================================
echo  1. Launch Chat App (Swing GUI)
echo  2. Launch Database Management CLI (ChatDemo)
echo  3. Exit
echo ========================================================
set /p "CHOICE=Enter choice [1-3]: "

if "%CHOICE%"=="1" goto :launch_app
if "%CHOICE%"=="2" goto :launch_db
if "%CHOICE%"=="3" exit /b 0

echo Invalid choice. Try again.
pause
goto :menu

:launch_app
call "%SCRIPT_DIR%launch_app.bat" %2 %3
exit /b %ERRORLEVEL%

:launch_db
call "%SCRIPT_DIR%launch_db.bat"
exit /b %ERRORLEVEL%
