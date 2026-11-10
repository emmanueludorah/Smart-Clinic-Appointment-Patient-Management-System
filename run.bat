@echo off
REM Runs SCAPMS from the project folder so data\clinic_data.txt is found.
cd /d "%~dp0"
if not exist SCAPMS.jar call build.bat
java -jar SCAPMS.jar %*
