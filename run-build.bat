@echo off
setlocal

if not defined JAVA_HOME (
    where java >nul 2>nul
    if errorlevel 1 (
        echo Install JDK 17 or newer and set JAVA_HOME or add java to PATH.
        exit /b 1
    )
)

call "%~dp0gradlew.bat" --no-daemon --max-workers=1 --console=plain :app:assembleDebug
exit /b %ERRORLEVEL%
