# AndroCam Webcam

Proyek pribadi: menjadikan kamera belakang HP Android sebagai webcam
untuk PC lewat kabel USB, tanpa Gradle/Android Studio - hanya
`javac` + Android SDK command-line tools + Python.

Dibuat untuk HP dengan Android 11 yang sering tidak didukung oleh
aplikasi webcam pihak ketiga.

## Struktur

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

## Perilaku kamera (disengaja, bukan bug)

- Notifikasi "AndroCam Webcam aktif" **selalu tampil** selama kamera
  hidup - ini bawaan sistem Android untuk foreground service kamera
  dan tidak dihilangkan.
- Streaming **otomatis berhenti sementara saat layar HP dikunci**,
  dan lanjut lagi begitu layar dibuka. Ini disengaja supaya kamera
  tidak pernah aktif tanpa sepengetahuan pemilik HP.

## Setup

### 1. Android SDK command-line tools
Download "cmdline-tools only", extract, lalu:
```
sdkmanager "platform-tools" "platforms;android-30" "build-tools;35.0.0"
```
Set `ANDROID_HOME` mengarah ke folder SDK-nya.

### 2. JDK 17+
Pastikan `javac` dan `keytool` ada di PATH.

### 3. Python + OBS Studio
```
pip install opencv-python pyvirtualcam numpy
```
Install OBS Studio, buka sekali, klik **Start Virtual Camera** lalu
**Stop Virtual Camera** (ini mendaftarkan driver ke Windows). OBS
tidak perlu dibuka lagi setelah itu.

### 4. HP
Aktifkan **Developer options → USB debugging**, colok USB, izinkan
popup debugging di HP.

## Cara pakai

```
build.bat        # sekali saja, atau saat source Java berubah
install.bat       # tiap kali mau dipakai
```

Setelah `install.bat` jalan, buka aplikasi "AndroCam Webcam" di HP,
tap **MULAI WEBCAM**. Pilih **"OBS Virtual Camera"** sebagai kamera
di aplikasi video call pilihanmu.

## Lisensi

Untuk penggunaan pribadi. Sesuaikan bebas untuk kebutuhan sendiri.
