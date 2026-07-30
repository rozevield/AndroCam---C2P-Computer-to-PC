@echo off
REM ============================================================
REM build.bat
REM Kompilasi APK dari source di folder android\ tanpa Gradle.
REM Jalankan ini SEKALI saja (atau tiap kali source Java/Manifest
REM berubah). Hasil akhir: IsengWebcam.apk di root folder ini,
REM siap dipakai berkali-kali oleh install.bat.
REM
REM SESUAIKAN dulu bagian ini sesuai PC-mu:
REM   - ANDROID_HOME harus sudah di-set sebagai environment variable
REM   - Ganti versi build-tools kalau bukan 35.0.0
REM ============================================================

set SDK=%ANDROID_HOME%
set BUILD_TOOLS=%SDK%\build-tools\35.0.0
set PLATFORM_JAR=%SDK%\platforms\android-36\android.jar
set SRC=android
set OUT=build_temp

if not exist %OUT% mkdir %OUT%

echo [1/6] Compile resources (aapt2)...
"%BUILD_TOOLS%\aapt2.exe" compile --dir %SRC%\res -o %OUT%\res.zip
if errorlevel 1 goto :error

echo [2/6] Link resources + manifest jadi base APK...
"%BUILD_TOOLS%\aapt2.exe" link -o %OUT%\base.apk ^
    -I %PLATFORM_JAR% ^
    --manifest %SRC%\AndroidManifest.xml ^
    -R %OUT%\res.zip ^
    --java %OUT%\gen ^
    --auto-add-overlay
if errorlevel 1 goto :error

echo [3/6] Compile Java (javac)...
javac --release 17 -classpath %PLATFORM_JAR% -d %OUT%\classes ^
    %SRC%\MainActivity.java %SRC%\CameraService.java ^
    %OUT%\gen\com\androcam\webcam\R.java
if errorlevel 1 goto :error

echo [4/6] Dex-kan class files (d8)...
dir /s /b %OUT%\classes\com\androcam\webcam\*.class > %OUT%\classlist.txt
call "%BUILD_TOOLS%\d8.bat" --output %OUT% --lib %PLATFORM_JAR% @%OUT%\classlist.txt
if errorlevel 1 goto :error

echo [5/6] Gabungkan classes.dex ke dalam APK...
cd %OUT%
jar uf base.apk classes.dex
cd ..

echo [6/6] Sign APK...
if not exist debug.keystore (
    keytool -genkey -v -keystore debug.keystore -alias androidkey ^
        -storepass android -keypass android ^
        -keyalg RSA -keysize 2048 -validity 10000 ^
        -dname "CN=Debug,O=Debug,C=ID"
)
call "%BUILD_TOOLS%\apksigner.bat" sign --ks debug.keystore --ks-pass pass:android ^
    --out IsengWebcam.apk %OUT%\base.apk
if errorlevel 1 goto :error

echo.
echo BUILD SELESAI: IsengWebcam.apk siap di folder ini.
echo Sekarang jalankan install.bat untuk memasang ke HP.
goto :eof

:error
echo.
echo *** BUILD GAGAL. Cek pesan error di atas. ***
pause
