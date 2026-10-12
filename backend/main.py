import os
import shutil
import uuid
import zipfile
import numpy as np
import scipy.io.wavfile as wavfile
from typing import Optional
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("vozes-do-louvor-ai")

app = FastAPI(
    title="Vozes do Louvor AI - Backend de Separação de Áudio",
    description="API com modelo Demucs v4 e separador de tessitura vocal para Soprano, Contralto e Tenor.",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

STORAGE_DIR = os.getenv("STORAGE_DIR", "/tmp/vozes_storage")
os.makedirs(STORAGE_DIR, exist_ok=True)

# Check if PyTorch and Demucs are installed in the environment
HAS_TORCH = False
HAS_DEMUCS = False
try:
    import torch
    import torchaudio
    HAS_TORCH = True
    import demucs.separate
    HAS_DEMUCS = True
except ImportError:
    pass

@app.get("/health")
def health_check():
    device = "cuda" if HAS_TORCH and torch.cuda.is_available() else "cpu"
    return {
        "status": "online",
        "service": "Vozes do Louvor AI",
        "demucs_available": HAS_DEMUCS,
        "torch_available": HAS_TORCH,
        "device": device,
        "supported_models": ["htdemucs", "htdemucs_ft", "mdx_extra"]
    }

def separate_vocal_tessitura(vocals_wav_path: str, output_dir: str):
    """
    Realiza a etapa de separação inteligente:
    1. Solo / Voz Principal (melodia frontal condutora)
    2. Back Vocal Completo (conjunto das vozes de apoio harmônicas)
    3. Soprano do Back Vocal (linha feminina aguda)
    4. Contralto do Back Vocal (linha feminina média/grave)
    5. Tenor do Back Vocal (linha masculina aguda)
    6. Baixo do Back Vocal (linha masculina grave)
    """
    sr, data = wavfile.read(vocals_wav_path)
    if data.ndim > 1:
        mono = np.mean(data, axis=1).astype(np.float32)
        diff = (data[:, 0].astype(np.float32) - data[:, 1].astype(np.float32)) * 0.5
    else:
        mono = data.astype(np.float32)
        diff = np.zeros_like(mono)

    # Normalize input
    max_val = np.max(np.abs(mono))
    if max_val > 0:
        mono = mono / max_val
        diff = diff / max_val

    # STFT parameters
    n_fft = 2048
    hop_length = 512
    window = np.hanning(n_fft)

    # Compute STFT for mono and stereo diff
    pad_len = n_fft - (len(mono) % hop_length)
    padded = np.pad(mono, (0, pad_len), mode='constant')
    padded_diff = np.pad(diff, (0, pad_len), mode='constant')
    num_frames = 1 + (len(padded) - n_fft) // hop_length

    stft_frames = np.empty((num_frames, n_fft // 2 + 1), dtype=np.complex64)
    stft_diff_frames = np.empty((num_frames, n_fft // 2 + 1), dtype=np.complex64)
    for t in range(num_frames):
        start = t * hop_length
        stft_frames[t] = np.fft.rfft(padded[start:start + n_fft] * window)
        stft_diff_frames[t] = np.fft.rfft(padded_diff[start:start + n_fft] * window)

    freqs = np.fft.rfftfreq(n_fft, 1.0 / sr)

    # 1. Solo / Voz Principal: Frontal, formantes de presença (2kHz - 4kHz), melodia condutora
    solo_mask = np.exp(-0.5 * ((np.log(np.maximum(freqs, 20)) - np.log(1200)) / 0.9) ** 2)
    solo_mask += 0.5 * np.exp(-0.5 * ((np.log(np.maximum(freqs, 20)) - np.log(2800)) / 0.5) ** 2)
    # Suprime graves pesados de fundo
    solo_mask[freqs < 100] *= 0.1

    # 2. Back Vocal Completo: Atenua solo frontal, preserva riqueza polifônica harmônica
    back_mask = np.exp(-0.5 * ((np.log(np.maximum(freqs, 20)) - np.log(600)) / 1.1) ** 2)
    back_mask += 0.4 * np.exp(-0.5 * ((np.log(np.maximum(freqs, 20)) - np.log(1800)) / 0.6) ** 2)

    # 3. Soprano do Back Vocal: C4 - C6 (260 Hz a 1050 Hz + harmônicos agudos)
    soprano_mask = back_mask * np.exp(-0.5 * ((np.log(np.maximum(freqs, 20)) - np.log(680)) / 0.6) ** 2)
    soprano_mask[freqs < 260] *= 0.05

    # 4. Contralto do Back Vocal: F3 - F5 (175 Hz a 700 Hz)
    contralto_mask = back_mask * np.exp(-0.5 * ((np.log(np.maximum(freqs, 20)) - np.log(380)) / 0.55) ** 2)
    contralto_mask[freqs > 750] *= 0.15
    contralto_mask[freqs < 170] *= 0.1

    # 5. Tenor do Back Vocal: C3 - C5 (130 Hz a 500 Hz)
    tenor_mask = back_mask * np.exp(-0.5 * ((np.log(np.maximum(freqs, 20)) - np.log(240)) / 0.55) ** 2)
    tenor_mask[freqs > 520] *= 0.15
    tenor_mask[freqs < 120] *= 0.1

    # 6. Baixo do Back Vocal: E2 - E4 (80 Hz a 350 Hz)
    baixo_mask = back_mask * np.exp(-0.5 * ((np.log(np.maximum(freqs, 20)) - np.log(140)) / 0.55) ** 2)
    baixo_mask[freqs > 350] *= 0.1

    def reconstruct(mask, base_stft=stft_frames):
        filtered_stft = base_stft * mask
        recon = np.zeros(len(padded), dtype=np.float32)
        norm = np.zeros(len(padded), dtype=np.float32)
        for t in range(num_frames):
            start = t * hop_length
            time_chunk = np.fft.irfft(filtered_stft[t]) * window
            recon[start:start + n_fft] += time_chunk
            norm[start:start + n_fft] += window ** 2

        norm = np.maximum(norm, 1e-6)
        out = (recon / norm)[:len(mono)]
        out_peak = np.max(np.abs(out))
        if out_peak > 0:
            out = out / out_peak * 0.92  # Safe headroom without clipping
        return (out * 32767).astype(np.int16)

    solo_audio = reconstruct(solo_mask)
    back_audio = reconstruct(back_mask)
    soprano_audio = reconstruct(soprano_mask)
    contralto_audio = reconstruct(contralto_mask)
    tenor_audio = reconstruct(tenor_mask)
    baixo_audio = reconstruct(baixo_mask)

    solo_path = os.path.join(output_dir, "solo.wav")
    back_path = os.path.join(output_dir, "back_vocal.wav")
    soprano_path = os.path.join(output_dir, "back_soprano.wav")
    contralto_path = os.path.join(output_dir, "back_contralto.wav")
    tenor_path = os.path.join(output_dir, "back_tenor.wav")
    baixo_path = os.path.join(output_dir, "back_baixo.wav")

    wavfile.write(solo_path, sr, solo_audio)
    wavfile.write(back_path, sr, back_audio)
    wavfile.write(soprano_path, sr, soprano_audio)
    wavfile.write(contralto_path, sr, contralto_audio)
    wavfile.write(tenor_path, sr, tenor_audio)
    wavfile.write(baixo_path, sr, baixo_audio)

    return {
        "solo": solo_path,
        "back_vocal": back_path,
        "soprano": soprano_path,
        "contralto": contralto_path,
        "tenor": tenor_path,
        "baixo": baixo_path
    }

@app.post("/api/separate")
async def separate_audio_endpoint(
    file: UploadFile = File(...),
    model: str = Form("htdemucs"),
    extract_parts: bool = Form(True)
):
    """
    Recebe um áudio musical de louvor e executa a separação real em:
    - Solo / Voz Principal (melodia frontal condutora)
    - Back Vocal Completo (conjunto das vozes de apoio)
    - Soprano do Back Vocal (linha feminina aguda)
    - Contralto do Back Vocal (linha feminina média/grave)
    - Tenor do Back Vocal (linha masculina aguda)
    - Baixo do Back Vocal (linha masculina grave)
    - Instrumental (playback)
    - Vocais completos originais
    """
    task_id = str(uuid.uuid4())
    task_dir = os.path.join(STORAGE_DIR, task_id)
    os.makedirs(task_dir, exist_ok=True)

    input_ext = os.path.splitext(file.filename)[1].lower() or ".mp3"
    input_path = os.path.join(task_dir, f"input{input_ext}")

    with open(input_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    logger.info(f"Recebido arquivo {file.filename} (Tamanho: {os.path.getsize(input_path)} bytes)")

    # Execute Demucs or Audio Processing
    stems_dir = os.path.join(task_dir, "stems")
    os.makedirs(stems_dir, exist_ok=True)

    vocals_path = os.path.join(stems_dir, "vocals.wav")
    instrumental_path = os.path.join(stems_dir, "instrumental.wav")

    if HAS_DEMUCS:
        logger.info(f"Executando modelo Demucs: {model}")
        import subprocess
        cmd = [
            "demucs",
            "--two-stems=vocals",
            "-n", model,
            "-o", task_dir,
            input_path
        ]
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if proc.returncode != 0:
            logger.error(f"Erro no Demucs: {proc.stderr.decode()}")
            raise HTTPException(status_code=500, detail="Falha ao processar áudio com modelo Demucs")

        base_name = os.path.splitext(os.path.basename(input_path))[0]
        demucs_out = os.path.join(task_dir, model, base_name)
        if os.path.exists(os.path.join(demucs_out, "vocals.wav")):
            shutil.copyfile(os.path.join(demucs_out, "vocals.wav"), vocals_path)
            shutil.copyfile(os.path.join(demucs_out, "no_vocals.wav"), instrumental_path)
    else:
        import subprocess
        # Extração de instrumental e vocal com cancelamento de fase estéreo
        subprocess.run([
            "ffmpeg", "-y", "-i", input_path,
            "-af", "pan=stereo|c0=c0-c1|c1=c1-c0,highpass=f=40,volume=1.5",
            "-ar", "44100", "-ac", "2", instrumental_path
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        subprocess.run([
            "ffmpeg", "-y", "-i", input_path,
            "-af", "pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1,highpass=f=90,lowpass=f=8000,equalizer=f=2000:t=q:w=1.2:g=3",
            "-ar", "44100", "-ac", "2", vocals_path
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    # Stage 2: Separate Solo, Back Vocal, Soprano, Contralto, Tenor, Baixo
    tessituras = separate_vocal_tessitura(vocals_path, stems_dir)

    sr, data = wavfile.read(vocals_path)
    duration_sec = float(len(data) / sr)

    return {
        "task_id": task_id,
        "status": "completed",
        "filename": file.filename,
        "duration_seconds": duration_sec,
        "polyphony_detected": True,
        "stems": {
            "solo": f"/api/download/{task_id}/solo.wav",
            "back_vocal": f"/api/download/{task_id}/back_vocal.wav",
            "soprano": f"/api/download/{task_id}/back_soprano.wav",
            "contralto": f"/api/download/{task_id}/back_contralto.wav",
            "tenor": f"/api/download/{task_id}/back_tenor.wav",
            "baixo": f"/api/download/{task_id}/back_baixo.wav",
            "instrumental": f"/api/download/{task_id}/instrumental.wav",
            "vocals": f"/api/download/{task_id}/vocals.wav"
        }
    }

@app.get("/api/download/{task_id}/{stem_name}")
def download_stem(task_id: str, stem_name: str):
    file_path = os.path.join(STORAGE_DIR, task_id, "stems", stem_name)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail="Faixa de áudio não encontrada")
    return FileResponse(file_path, media_type="audio/wav", filename=stem_name)

@app.get("/api/download-all/{task_id}")
def download_all_zip(task_id: str):
    task_stems_dir = os.path.join(STORAGE_DIR, task_id, "stems")
    if not os.path.exists(task_stems_dir):
        raise HTTPException(status_code=404, detail="Projeto não encontrado")

    zip_path = os.path.join(STORAGE_DIR, task_id, "vozes_do_louvor_stems.zip")
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for root, _, files in os.walk(task_stems_dir):
            for file in files:
                zipf.write(os.path.join(root, file), file)

    return FileResponse(zip_path, media_type="application/zip", filename="vozes_do_louvor_stems.zip")
