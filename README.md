# AndroCam - C2P Webcam

A personal project: using an Android phone's rear camera as a PC webcam
via USB cable, without Gradle or Android Studio—using only
`javac`, Android SDK command-line tools, and Python.

Created for Android 11 phones, which are often unsupported by
third-party webcam apps.

## Structure

```
.
├── android/              Source Java + Manifest untuk aplikasi HP
│   ├── AndroidManifest.xml
│   ├── MainActivity.java
│   ├── CameraService.java
│   └── res/values/strings.xml
├── build.bat              Compile APK (jalankan sekali, atau saat source berubah)
├── install.bat             Install APK ke HP + jalankan client PC
├── client_pc.py            Penerima frame di PC, menyuntik ke OBS Virtual Camera
└── debug.keystore          Dibuat otomatis oleh build.bat saat pertama kali
```

## Camera behavior (intended, not a bug)

- Do not panic if you do not see any window or display on your phone screen when opening the app; it is designed to be UI-less, so the app immediately 
  closing or showing a blank screen is perfectly normal behavior. To verify that it is working correctly, simply check your phone's notification bar; if an active notification reading "Menunggu koneksi PC..." appears, the app is successfully running in the background, with the phone camera automatically ready and streaming data to the PC—even when the screen is turned off or locked.
- The "AndroCam Webcam active" notification **always appears** while the camera
  is running; this is standard Android system behavior for foreground
  camera services and cannot be removed.
- Streaming is **still running even when the phone screen is locked**.
  This ensures the camera still transmited even when the phone screen is locked.

## Setup

### 1. Android SDK command-line tools
Download "cmdline-tools only", extract, lalu:
```
sdkmanager "platform-tools" "platforms;android-30" "build-tools;35.0.0"
```
Set `ANDROID_HOME` to point to the SDK folder.

### 2. JDK 17+
Ensure `javac` and `keytool` are in your PATH.

### 3. Python + OBS Studio
```
pip install opencv-python pyvirtualcam numpy
```
Install OBS Studio, open it once, click **Start Virtual Camera**, then
**Stop Virtual Camera** (this registers the driver with Windows). OBS
does not need to be kept open after this. 

### 4. Phone
Enable **Developer options → USB debugging**, connect the USB cable, and allow
the debugging prompt on your phone.

## Usage

```
build.bat         # run once, or whenever the Java source changes
install.bat       # run once, to ensure the application is installed on your mobile device
run.bat           # run each time to start the client
```

After running `install.bat`, open the "C2P" app on your phone
and tap **START WEBCAM**. Select **"OBS Virtual Camera"** as the camera
in your preferred video calling application.

## License

For personal use. Feel free to customize it to suit your needs.