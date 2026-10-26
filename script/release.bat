@echo off
setlocal

cd /d "%~dp0.."

set "ANDROID_HOME=D:\86133\Android\Sdk"
set "ANDROID_SDK_ROOT=D:\86133\Android\Sdk"
set "GRADLE_USER_HOME=%CD%\.gradle-home"
set "PROJECT_CACHE_DIR=%CD%\out\project-cache"

echo [1/2] Checking submodules...
if not exist "%CD%\love\src\main\jniLibs" (
    echo Warning: submodules may not be initialized.
    echo Run: git submodule update --init --recursive
)

echo [2/2] Building release APK...
call "%CD%\gradlew.bat" :app:assembleNormalRecordRelease --project-cache-dir "%PROJECT_CACHE_DIR%" --no-daemon
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
    echo Build succeeded.
    echo APK: %CD%\out\app\outputs\apk\normalRecord\release\app-normal-record-release.apk
) else (
    echo Build failed with exit code %EXIT_CODE%.
)

echo.
pause
exit /b %EXIT_CODE%
