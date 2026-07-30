"""
client_pc.py

Client PC untuk AndroCam Webcam. Terhubung ke port yang sudah
di-forward dari HP (lewat `adb forward tcp:47623 tcp:47623` di
install.bat). CameraService di HP bertindak sebagai server yang
menunggu koneksi masuk; skrip ini yang aktif menghubungi.

Loop luar: kalau HP belum membuka aplikasi kameranya, atau koneksi
terputus (mis. aplikasi kamera di HP ditutup / layar dikunci lama),
skrip ini TIDAK berhenti - otomatis mencoba menyambung ulang setiap
beberapa detik. Tutup dengan Ctrl+C kalau mau berhenti manual.
"""

import socket
import struct
import time
import subprocess
import re
import numpy as np
import cv2
import pyvirtualcam

HOST = "127.0.0.1"
PORT = 47623
WIDTH, HEIGHT = 1280, 720
RETRY_DELAY_SEC = 2


def recv_exact(sock: socket.socket, n: int) -> bytes:
    """Terima persis n byte (TCP bisa mengirim data terpecah-pecah)."""
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("Koneksi HP terputus")
        buf += chunk
    return buf


def connect_once() -> socket.socket:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    s.connect((HOST, PORT))
    s.settimeout(None)
    return s


def stream_loop(conn: socket.socket, cam: "pyvirtualcam.Camera") -> None:
    """Terima frame terus-menerus sampai koneksi putus."""
    while True:
        header = recv_exact(conn, 4)
        frame_len = struct.unpack(">I", header)[0]
        jpeg_bytes = recv_exact(conn, frame_len)

        np_arr = np.frombuffer(jpeg_bytes, dtype=np.uint8)
        frame_bgr = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        if frame_bgr is None:
            continue

        frame_bgr = cv2.resize(frame_bgr, (WIDTH, HEIGHT))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)

        cam.send(frame_rgb)
        cam.sleep_until_next_frame()

def kill_port_owner(port):
    """Mencari dan membunuh proses di Windows yang mengunci port tertentu."""
    print(f"[client_pc.py] Memeriksa apakah port {port} sedang digunakan...")
    try:
        # Jalankan netstat untuk mencari PID yang menggunakan port tersebut
        result = subprocess.run(
            ["netstat", "-ano"], 
            capture_output=True, 
            text=True, 
            check=True
        )
        
        # Cari baris yang mengandung port kita (contoh: :47623)
        pattern = rf":{port}\s+.*?\s+LISTENING\s+(\d+)"
        match = re.search(pattern, result.stdout)
        
        if match:
            pid = match.group(1)
            print(f"[client_pc.py] Ditemukan proses dengan PID {pid} mengunci port {port}. Membunuh proses...")
            # Paksa bunuh proses berdasarkan PID-nya (/F = Force, /PID = Process ID)
            subprocess.run(["taskkill", "/F", "/PID", pid], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print("[client_pc.py] Port berhasil dibersihkan.")
            time.sleep(1) # Beri jeda 1 detik agar OS melepaskan port sepenuhnya
        else:
            print("[client_pc.py] Port aman, tidak ada aplikasi lain yang menggunakan.")
    except Exception as e:
        print(f"[client_pc.py] Gagal membersihkan port secara otomatis: {e}")

def main():
    print(f"[client_pc.py] AndroCam client aktif. Target: {HOST}:{PORT}")

    try:
        print("[client_pc.py] Mengonfigurasi jembatan ADB forward...")
        subprocess.run(["adb", "forward", "tcp:47623", "tcp:47623"], check=True, stdout=subprocess.DEVNULL)
        print("[client_pc.py] ADB forward berhasil dikonfigurasi.")
    except Exception as e:
        print(f"[client_pc.py] Gagal menjalankan ADB forward: {e}")
        print("[client_pc.py] Pastikan HP sudah dicolok dan perintah 'adb' terdaftar di PATH Environment.")

    with pyvirtualcam.Camera(width=WIDTH, height=HEIGHT, fps=20) as cam:
        print(f"[client_pc.py] Virtual camera siap: {cam.device}")

        while True:
            try:
                print("[client_pc.py] Menunggu HP membuka aplikasi kamera...")
                conn = connect_once()
                print("[client_pc.py] Terhubung ke HP - streaming dimulai.")
                stream_loop(conn, cam)

            except (ConnectionRefusedError, ConnectionError,
                    socket.timeout, OSError) as e:
                print(f"[client_pc.py] {e} - mencoba lagi dalam "
                      f"{RETRY_DELAY_SEC} detik...")
                time.sleep(RETRY_DELAY_SEC)

            except KeyboardInterrupt:
                print("[client_pc.py] Dihentikan oleh pengguna.")
                break


if __name__ == "__main__":
    main()
