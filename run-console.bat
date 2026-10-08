@echo off
rem ---- JVBot console version: compile + run in the terminal ----
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\jvbot\*.java src\jvbot\gui\*.java
if errorlevel 1 (
    echo.
    echo Compile failed - fix the errors above and run again.
    exit /b 1
)
java -cp out jvbot.Main
