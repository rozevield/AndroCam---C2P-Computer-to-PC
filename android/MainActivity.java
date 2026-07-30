package com.androcam.webcam;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Trampoline Activity: Tidak memiliki UI sama sekali.
 * Berfungsi menjembatani pengecekan izin sebelum melempar tugas ke CameraService.
 */
public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Tanpa setContentView() agar tidak ada layout yang dirender
        requestCameraThenStart();
    }

    private void requestCameraThenStart() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Jika belum ada izin, sistem Android tetap akan memunculkan dialog pop-up bawaan OS
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCameraService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraService();
        } else {
            Toast.makeText(this, "Izin kamera ditolak. Aplikasi tidak dapat berjalan.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void startCameraService() {
        Intent intent = new Intent(this, CameraService.class);
        startForegroundService(intent);
        
        // Hancurkan activity seketika agar aplikasi kembali ke homescreen pemilik HP
        finish(); 
    }
}