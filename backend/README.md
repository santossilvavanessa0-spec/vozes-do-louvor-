# Servidor de Separação de Vozes - Vozes do Louvor AI

Backend de processamento de áudio com modelo **Demucs v4** e motor de decomposição de tessitura vocal para **Soprano**, **Contralto**, **Tenor**, **Baixo** e **Instrumental**.

---

## 1. Conexão por Ambiente

O aplicativo Android suporta múltiplos ambientes configuráveis na aba **Ajustes**:

* **Emulador Android:** Utilize `http://10.0.2.2:8001` (o alias `10.0.2.2` aponta para o computador hospedeiro).
* **Dispositivo Físico (Celular via Wi-Fi):** Conecte o celular na mesma rede Wi-Fi do computador e utilize o IP da sua máquina local: `http://192.168.X.X:8001` (substitua pelo IP obtido com `ipconfig` no Windows ou `ip a` no Linux/Mac).
* **Servidor em Nuvem:** Se hospedado remotamente (ex: AWS, GCP, RunPod, HuggingFace), utilize a URL pública HTTPS: `https://seu-servidor-demucs.com`.

---

## 2. Como Iniciar com Docker

```bash
cd backend
docker compose up -d --build
```

O container mapeia as portas **8001** e **8000** e processa os arquivos com suporte automático a GPU NVIDIA quando disponível.

---

## 3. Como Iniciar Diretamente com Python

```bash
cd backend
python3 server.py
```

O servidor iniciará automaticamente ouvindo em `http://0.0.0.0:8001`.

---

## 4. Teste Rápido de Verificação

Para confirmar que o servidor está online e pronto:

```bash
# Testar endpoint de saúde:
curl -s http://localhost:8001/health

# Resposta esperada:
# {"status": "online", "service": "Vozes do Louvor AI Backend", "demucs_available": true, "port": 8001}
```

---

## 5. Endpoints da API

* `GET /health`: Retorna status, porta e disponibilidade dos motores de separação.
* `POST /api/separate`: Envia o áudio em `multipart/form-data` (`file`) e devolve os links das faixas vocais e instrumentais isoladas.
* `GET /api/download/{task_id}/{stem}`: Baixa a faixa de áudio WAV gerada.
* `GET /api/download-all/{task_id}`: Baixa o arquivo `.zip` com todas as faixas do louvor.
