@echo off
REM ============================================================
REM install.bat
REM Pasang APK yang sudah dibuild (lewat build.bat) ke HP,
REM buka jalur data USB, lalu jalankan client Python.
REM Tidak perlu compile ulang tiap kali - build.bat cukup sekali.
REM ============================================================

if not exist IsengWebcam.apk (
    echo IsengWebcam.apk belum ada. Jalankan build.bat dulu.
    pause
    goto :eof
)

echo [1/3] Pasang APK ke HP...
adb start-server
adb install -r IsengWebcam.apk
if errorlevel 1 goto :error

echo [2/3] Buka jalur data lewat USB...
adb forward tcp:47623 tcp:47623

echo [3/3] Menjalankan client Python...
pip install opencv-python pyvirtualcam numpy --quiet
python client_pc.py
goto :eof
pause

:error
echo.
echo *** INSTALL GAGAL. Pastikan HP terhubung dan "adb devices" mendeteksinya. ***
pause
