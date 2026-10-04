@echo off
title Compression Engine v0.1 - Web Dashboard
echo ===================================================
echo   COMPRESSION ENGINE v0.1 - WEB DASHBOARD
echo   Compiling and launching on http://localhost:8080
echo ===================================================

if not exist java\target\classes mkdir java\target\classes

echo Compiling Java sources...
dir /s /b java\src\main\*.java > sources.txt
javac -d java\target\classes @sources.txt
del sources.txt

echo Starting Web Server...
start "" "http://localhost:8080"
java -cp java\target\classes com.compression.cli.Main server 8080
pause
