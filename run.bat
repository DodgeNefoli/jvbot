@echo off
rem ---- JVBot desktop app: compile + open the window ----
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\jvbot\*.java src\jvbot\gui\*.java
if errorlevel 1 (
    echo.
    echo Compile failed - fix the errors above and run again.
    exit /b 1
)
start "JVBot" javaw -cp out jvbot.gui.GuiMain
