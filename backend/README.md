# Servidor de Separação de Vozes - Vozes do Louvor AI

Backend oficial de processamento de áudio com modelo **Demucs v4** e motor de decomposição de tessitura vocal para **Soprano**, **Contralto**, **Tenor** e **Instrumental**.

---

## 1. Como Executar com Docker (Recomendado)

Se você possui o Docker instalado (em seu computador local ou servidor com GPU):

```bash
cd backend
docker compose up -d --build
```

O servidor iniciará automaticamente em: `http://localhost:8000` (ou `http://10.0.2.2:8000` no emulador Android).

---

## 2. Como Executar Diretamente com Python

```bash
cd backend
python -m venv venv
source venv/bin/activate  # No Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

---

## 3. Endpoints da API

* `GET /health`: Verifica o status do servidor, GPU e se o Demucs está carregado.
* `POST /api/separate`: Envia o áudio (`multipart/form-data`) e retorna as URLs das faixas reais:
  * `soprano`: Voz feminina aguda
  * `contralto`: Voz feminina média/grave
  * `tenor`: Voz masculina aguda
  * `baixo`: Voz masculina grave
  * `instrumental`: Playback sem vozes
  * `vocals`: Faixa vocal completa isolada
* `GET /api/download/{task_id}/{stem}`: Download da faixa em formato WAV real.
* `GET /api/download-all/{task_id}`: Download do pacote ZIP com todas as faixas.
