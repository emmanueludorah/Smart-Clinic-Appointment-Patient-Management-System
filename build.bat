@echo off
REM ------------------------------------------------------------------
REM  SCAPMS - build script (Windows). Requires JDK 17 or newer.
REM  Compiles all sources into .\out and packages SCAPMS.jar
REM ------------------------------------------------------------------
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac --release 17 -encoding UTF-8 -d out src\scapms\*.java src\scapms\exception\*.java src\scapms\model\*.java src\scapms\util\*.java src\scapms\controller\*.java src\scapms\gui\*.java
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)
jar --create --file SCAPMS.jar --main-class scapms.Main -C out .
echo.
echo Build successful. Run with:  run.bat   (or: java -jar SCAPMS.jar)
