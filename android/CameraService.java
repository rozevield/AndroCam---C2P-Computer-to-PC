package com.androcam.webcam;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class CameraService extends Service {

    private static final String CHANNEL_ID = "androcam_channel";
    private static final int NOTIF_ID = 1;
    private static final int TCP_PORT = 47623;
    private static final String ACTION_STOP = "com.androcam.webcam.ACTION_STOP";

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private ExecutorService socketExecutor;
    private Socket clientSocket;
    private volatile boolean streaming = false;
    private PowerManager.WakeLock wakeLock;

    // OPTIMASI: Sinkronisasi buffer gambar untuk memisahkan thread kamera & jaringan
    private final Object frameLock = new Object();
    private byte[] latestFrameBytes = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Menunggu koneksi PC..."));

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroCam::CameraWakeLock");
            wakeLock.acquire();
        }

        // OPTIMASI 1: Naikkan prioritas thread ke THREAD_PRIORITY_VIDEO agar diutamakan oleh OS
        bgThread = new HandlerThread("CameraBg", android.os.Process.THREAD_PRIORITY_VIDEO);
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());

        // OPTIMASI 2: Ubah ke CachedThreadPool agar bisa menjalankan server & loop pengirim secara paralel
        socketExecutor = Executors.newCachedThreadPool();
        socketExecutor.execute(this::runSocketServer);

        openCamera();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, CameraService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AndroCam Webcam")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "HENTIKAN", stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "AndroCam", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private void openCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String backCameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = id;
                    break;
                }
            }
            if (backCameraId == null) return;

            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(this::onFrameAvailable, bgHandler);

            manager.openCamera(backCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice device) {
                    cameraDevice = device;
                    startCaptureSession();
                }
                @Override
                public void onDisconnected(CameraDevice device) { device.close(); }
                @Override
                public void onError(CameraDevice device, int error) { device.close(); }
            }, bgHandler);

        } catch (CameraAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void startCaptureSession() {
        try {
            // OPTIMASI 3: Menggunakan TEMPLATE_PREVIEW untuk latensi hardware terendah
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(
                    java.util.Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                streaming = true;
                                session.setRepeatingRequest(builder.build(), null, bgHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {}
                    }, bgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void onFrameAvailable(ImageReader reader) {
        try (Image img = reader.acquireLatestImage()) {
            if (img == null || !streaming || clientSocket == null) return;
            
            ByteBuffer buffer = img.getPlanes()[0].getBuffer();
            byte[] jpegBytes = new byte[buffer.remaining()];
            buffer.get(jpegBytes);
            
            // OPTIMASI 4: Masukkan ke RAM saja, jangan lakukan I/O Network di thread ini
            synchronized (frameLock) {
                latestFrameBytes = jpegBytes;
                frameLock.notifyAll(); // Bangunkan thread pengirim jika sedang menunggu
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runSocketServer() {
        try (ServerSocket server = new ServerSocket(TCP_PORT)) {
            while (true) {
                Socket socket = server.accept();
                clientSocket = socket;
                updateNotification("Terhubung ke PC — streaming aktif");
                
                // OPTIMASI 5: Jalankan loop pengirim data di thread terpisah secara asinkron
                socketExecutor.execute(this::sendLoop);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // OPTIMASI 6: Loop khusus pengirim network (Consumer)
    private void sendLoop() {
        while (streaming && clientSocket != null && !clientSocket.isClosed()) {
            byte[] bytesToSend = null;
            
            synchronized (frameLock) {
                while (latestFrameBytes == null && streaming && clientSocket != null) {
                    try {
                        frameLock.wait(10); // Tunggu frame baru dari kamera
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                if (!streaming || clientSocket == null) break;
                
                bytesToSend = latestFrameBytes;
                latestFrameBytes = null; // Reset setelah diambil agar tidak mengirim duplikat
            }

            if (bytesToSend != null) {
                sendFrame(bytesToSend);
            }
        }
    }

    private void sendFrame(byte[] jpegBytes) {
        try {
            OutputStream out = clientSocket.getOutputStream();
            ByteBuffer header = ByteBuffer.allocate(4).putInt(jpegBytes.length);
            out.write(header.array());
            out.write(jpegBytes);
            out.flush();
        } catch (Exception e) {
            clientSocket = null; 
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        streaming = false;
        synchronized (frameLock) {
            frameLock.notifyAll();
        }
        if (captureSession != null) captureSession.close();
        if (cameraDevice != null) cameraDevice.close();
        if (imageReader != null) imageReader.close();
        if (bgThread != null) bgThread.quitSafely();
        if (socketExecutor != null) socketExecutor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}