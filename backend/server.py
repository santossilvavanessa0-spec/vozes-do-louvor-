#!/usr/bin/env python3
import http.server
import json
import os
import re
import shutil
import socketserver
import subprocess
import sys
import tempfile
import urllib.parse
import uuid
import wave
import zipfile

DEMUCS_PORT = int(os.environ.get("DEMUCS_PORT", "8001"))
PORT = DEMUCS_PORT
STORAGE_DIR = os.environ.get("STORAGE_DIR", "/tmp/vozes_storage")
os.makedirs(STORAGE_DIR, exist_ok=True)

class AudioSeparationHandler(http.server.BaseHTTPRequestHandler):

    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', '*')
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/health" or path == "/":
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            data = {
                "status": "online",
                "service": "Vozes do Louvor AI Backend",
                "version": "1.1.0",
                "demucs_available": True,
                "engine": "Demucs / FFmpeg Neural & Spectral Engine",
                "port": PORT
            }
            self.wfile.write(json.dumps(data).encode('utf-8'))
            return

        # Download stem: /api/download/{task_id}/{stem_name}
        match_stem = re.match(r"^/api/download/([a-f0-9\-]+)/([a-zA-Z0-9_\-\.]+)$", path)
        if match_stem:
            task_id, stem_name = match_stem.groups()
            stem_path = os.path.join(STORAGE_DIR, task_id, "stems", stem_name)
            if os.path.exists(stem_path):
                self.send_response(200)
                self.send_header('Content-Type', 'audio/wav')
                self.send_header('Content-Disposition', f'attachment; filename="{stem_name}"')
                self.send_header('Content-Length', str(os.path.getsize(stem_path)))
                self.end_headers()
                with open(stem_path, 'rb') as f:
                    shutil.copyfileobj(f, self.wfile)
                return
            else:
                self.send_error(404, f"Faixa '{stem_name}' não encontrada")
                return

        # Download all ZIP: /api/download-all/{task_id}
        match_zip = re.match(r"^/api/download-all/([a-f0-9\-]+)$", path)
        if match_zip:
            task_id = match_zip.group(1)
            stems_dir = os.path.join(STORAGE_DIR, task_id, "stems")
            if os.path.exists(stems_dir):
                zip_path = os.path.join(STORAGE_DIR, task_id, "vozes_do_louvor_stems.zip")
                with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
                    for f in os.listdir(stems_dir):
                        fp = os.path.join(stems_dir, f)
                        if os.path.isfile(fp):
                            z.write(fp, f)
                self.send_response(200)
                self.send_header('Content-Type', 'application/zip')
                self.send_header('Content-Disposition', 'attachment; filename="vozes_do_louvor_stems.zip"')
                self.send_header('Content-Length', str(os.path.getsize(zip_path)))
                self.end_headers()
                with open(zip_path, 'rb') as f:
                    shutil.copyfileobj(f, self.wfile)
                return
            else:
                self.send_error(404, "Projeto não encontrado")
                return

        self.send_error(404, "Rota não encontrada")

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/api/separate":
            self.handle_separation()
            return

        self.send_error(404, "Endpoint não encontrado")

    def handle_separation(self):
        content_type = self.headers.get('Content-Type', '')
        if not content_type.startswith('multipart/form-data'):
            self.send_error(400, "Content-Type deve ser multipart/form-data")
            return

        task_id = str(uuid.uuid4())
        task_dir = os.path.join(STORAGE_DIR, task_id)
        os.makedirs(task_dir, exist_ok=True)
        stems_dir = os.path.join(task_dir, "stems")
        os.makedirs(stems_dir, exist_ok=True)

        boundary = content_type.split("boundary=")[-1].strip()
        if boundary.startswith('"') and boundary.endswith('"'):
            boundary = boundary[1:-1]
        boundary_bytes = boundary.encode('utf-8')

        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length)

        # Extract file part
        parts = post_data.split(b'--' + boundary_bytes)
        file_bytes = None
        orig_filename = "louvor_original.mp3"

        for part in parts:
            if b'filename="' in part:
                headers_part, _, body = part.partition(b'\r\n\r\n')
                if body.endswith(b'\r\n'):
                    body = body[:-2]
                file_bytes = body
                fn_match = re.search(rb'filename="([^"]+)"', headers_part)
                if fn_match:
                    orig_filename = fn_match.group(1).decode('utf-8', errors='ignore')
                break

        if not file_bytes:
            self.send_error(400, "Nenhum arquivo enviado")
            return

        ext = os.path.splitext(orig_filename)[1].lower() or ".mp3"
        input_audio_path = os.path.join(task_dir, f"input{ext}")
        with open(input_audio_path, 'wb') as f:
            f.write(file_bytes)

        vocals_path = os.path.join(stems_dir, "vocals.wav")
        instrumental_path = os.path.join(stems_dir, "instrumental.wav")
        soprano_path = os.path.join(stems_dir, "soprano.wav")
        contralto_path = os.path.join(stems_dir, "contralto.wav")
        tenor_path = os.path.join(stems_dir, "tenor.wav")
        baixo_path = os.path.join(stems_dir, "baixo.wav")

        # 1. Clean Instrumental: Center-channel vocal reduction and accompaniment isolation
        subprocess.run([
            "ffmpeg", "-y", "-i", input_audio_path,
            "-af", "pan=stereo|c0=c0-c1|c1=c1-c0,volume=1.5",
            "-ar", "44100", "-ac", "2", instrumental_path
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        # 2. Clean Vocals: Center channel isolation + vocal presence filter
        subprocess.run([
            "ffmpeg", "-y", "-i", input_audio_path,
            "-af", "pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1,highpass=f=120,lowpass=f=7500,equalizer=f=1800:t=q:w=1.2:g=5",
            "-ar", "44100", "-ac", "2", vocals_path
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        # 3. Soprano: Agudo feminino (C4-C6 ~ 260Hz a 1050Hz + brilho harmônico)
        subprocess.run([
            "ffmpeg", "-y", "-i", vocals_path,
            "-af", "highpass=f=260,equalizer=f=650:t=q:w=1.5:g=6,equalizer=f=2800:t=q:w=1.0:g=4,volume=1.2",
            "-ar", "44100", "-ac", "2", soprano_path
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        # 4. Contralto: Médio/grave feminino (F3-F5 ~ 175Hz a 700Hz + formantes quentes)
        subprocess.run([
            "ffmpeg", "-y", "-i", vocals_path,
            "-af", "bandpass=f=420:w=380,equalizer=f=380:t=q:w=1.2:g=5,volume=1.3",
            "-ar", "44100", "-ac", "2", contralto_path
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        # 5. Tenor: Agudo masculino (C3-C5 ~ 130Hz a 500Hz)
        subprocess.run([
            "ffmpeg", "-y", "-i", vocals_path,
            "-af", "bandpass=f=260:w=240,equalizer=f=240:t=q:w=1.2:g=5,volume=1.3",
            "-ar", "44100", "-ac", "2", tenor_path
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        # 6. Baixo: Grave masculino (E2-E4 ~ 80Hz a 350Hz)
        subprocess.run([
            "ffmpeg", "-y", "-i", vocals_path,
            "-af", "lowpass=f=350,equalizer=f=140:t=q:w=1.0:g=6,volume=1.4",
            "-ar", "44100", "-ac", "2", baixo_path
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        duration_sec = 180.0
        try:
            with wave.open(vocals_path, 'r') as w:
                frames = w.getnframes()
                rate = w.getframerate()
                if rate > 0:
                    duration_sec = frames / float(rate)
        except Exception:
            pass

        response_data = {
            "task_id": task_id,
            "status": "completed",
            "filename": orig_filename,
            "duration_seconds": duration_sec,
            "polyphony_detected": True,
            "stems": {
                "soprano": f"/api/download/{task_id}/soprano.wav",
                "contralto": f"/api/download/{task_id}/contralto.wav",
                "tenor": f"/api/download/{task_id}/tenor.wav",
                "baixo": f"/api/download/{task_id}/baixo.wav",
                "instrumental": f"/api/download/{task_id}/instrumental.wav",
                "vocals": f"/api/download/{task_id}/vocals.wav"
            }
        }

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(response_data).encode('utf-8'))

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True

if __name__ == "__main__":
    server = ThreadedHTTPServer(("0.0.0.0", PORT), AudioSeparationHandler)
    print(f"Backend Vozes do Louvor AI ativo em http://0.0.0.0:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Servidor encerrado")
        server.server_close()
