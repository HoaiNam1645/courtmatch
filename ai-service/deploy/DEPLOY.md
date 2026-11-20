# Trien khai AI service len server (courtmatch.tech)

Server dung chung (1 vCPU, 2GB RAM, da dung ~1,4GB): AI service chay voi tran bo nho cung (MemoryMax 320M) va CPU 40%.
Huan luyen KHONG chay tren server; huan luyen o may local roi upload artifacts.

```bash
# 1. May local: huan luyen va kiem tra
cd ai-service && uv run python -m train partner && uv run pytest

# 2. Server (mot lan): tao thu muc, venv, cai goi phuc vu, token
ssh root@SERVER 'mkdir -p /opt/courtly/ai-service/artifacts && chown -R courtly:courtly /opt/courtly/ai-service'
scp -r courtly_ai requirements-serve.txt root@SERVER:/opt/courtly/ai-service/
ssh root@SERVER 'cd /opt/courtly/ai-service && python3 -m venv .venv && .venv/bin/pip install -q -r requirements-serve.txt \
  && umask 077 && echo "AI_SERVICE_TOKEN=$(openssl rand -hex 32)" > .env && chown courtly:courtly .env && chown -R courtly:courtly .'
scp deploy/courtly-ai.service root@SERVER:/etc/systemd/system/ && ssh root@SERVER 'systemctl daemon-reload && systemctl enable --now courtly-ai'

# 3. Moi lan co model moi: upload artifact (khong commit), roi nhap + kich hoat o /admin/matching
scp -r artifacts/partner-catboost-XXXX root@SERVER:/opt/courtly/ai-service/artifacts/
```

Backend can cung `AI_SERVICE_TOKEN` (cung gia tri trong `/opt/courtly/backend/.env`), `AI_SERVICE_URL=http://127.0.0.1:8001`
va `AI_ARTIFACT_DIR=/opt/courtly/ai-service/artifacts`. Thieu token thi backend tu dung baseline.
Kiem tra: `curl -s http://127.0.0.1:8001/health` tren server.
