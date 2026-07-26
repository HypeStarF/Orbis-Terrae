@echo off
setlocal enabledelayedexpansion
set "GRADLE_VERSION=9.2.1"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "INSTALL_ROOT=%GRADLE_USER_HOME%\orbis-terrae-bootstrap"
set "GRADLE_HOME=%INSTALL_ROOT%\gradle-%GRADLE_VERSION%"
set "ARCHIVE=%INSTALL_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
set "URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
    if not exist "%INSTALL_ROOT%" mkdir "%INSTALL_ROOT%"
    if not exist "%ARCHIVE%" (
        echo Downloading Gradle %GRADLE_VERSION%...
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ARCHIVE%'"
        if errorlevel 1 exit /b 1
    )
    if exist "%GRADLE_HOME%" rmdir /s /q "%GRADLE_HOME%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%ARCHIVE%' -DestinationPath '%INSTALL_ROOT%'"
    if errorlevel 1 exit /b 1
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %errorlevel%
