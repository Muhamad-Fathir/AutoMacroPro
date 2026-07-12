@echo off
setlocal

echo ============================================
echo   AutoMacro Pro - Build to Windows .exe
echo ============================================

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven (mvn) tidak ditemukan di PATH.
    echo         Install Maven, atau jalankan build ini dari "Embedded Terminal" NetBeans.
    exit /b 1
)

where jpackage >nul 2>nul
if errorlevel 1 (
    echo [ERROR] jpackage tidak ditemukan di PATH.
    echo         jpackage ada di dalam JDK 14+. Pastikan JAVA_HOME\bin masuk PATH.
    exit /b 1
)

echo.
echo [1/2] Membangun fat-jar lewat Maven (mvn clean package)...
call mvn clean package -q
if errorlevel 1 (
    echo [ERROR] Build Maven gagal. Periksa pesan error di atas.
    exit /b 1
)

if not exist dist mkdir dist

set APP_NAME=AutoMacro Pro
set APP_VERSION=1.0.0
set MAIN_JAR=AutoMacroPro.jar
set MAIN_CLASS=com.automacropro.Main

echo.
echo [2/2] Membungkus jar menjadi installer .exe lewat jpackage...
echo       (Butuh WiX Toolset 3.x di PATH untuk --type exe)
echo.

jpackage --type exe --input target --dest dist --name "%APP_NAME%" --app-version %APP_VERSION% --main-jar %MAIN_JAR% --main-class %MAIN_CLASS% --win-menu --win-shortcut --vendor "AutoMacro Pro"

if errorlevel 1 (
    echo.
    echo [ERROR] jpackage gagal. Kalau errornya terkait WiX/MSI, coba dulu app-image
    echo         (tidak butuh installer/WiX, hasil berupa folder siap-pakai):
    echo.
    echo   jpackage --type app-image --input target --dest dist --name "%APP_NAME%" --main-jar %MAIN_JAR% --main-class %MAIN_CLASS%
    echo.
    exit /b 1
)

echo.
echo Selesai! Installer .exe ada di folder dist\
endlocal
