# Courtly AI service

Huan luyen va suy luan model AI cua Courtly (2.2.11 - 2.2.19):
- **Xep hang doi tac**: CatBoostRanker (`YetiRankPairwise`), nhom xep hang theo nguoi tim.
- **Chia doi**: CatBoostClassifier du doan xac suat doi 1 thang tren nen Elo; do can bang = `100 * (1 - |2p - 1|)`.
- Spring Boot van loc dieu kien bat buoc, luu ket qua va xu ly nghiep vu; AI loi/qua han/chua co model thi Spring dung baseline.

## Chay

```bash
cd ai-service
uv sync

# Huan luyen (doc truc tiep cac bang cua he thong bang ket noi chi-doc; mac dinh postgresql://courtly:courtly@localhost:5432/courtly)
uv run python -m train partner      # xep hang doi tac
uv run python -m train team         # chia doi
#   --seed 42  --database-url postgresql://...
# Ket qua: artifacts/{modelVersion}/model.cbm, metadata.json, feature_importance.json  (artifacts/ KHONG dua len git)

# Chay service (chi lang nghe 127.0.0.1, bat buoc co token noi bo)
AI_SERVICE_TOKEN=doi-token-dai-ngau-nhien uv run uvicorn courtly_ai.service:app --host 127.0.0.1 --port 8001

uv run pytest
```

## Bien moi truong
| Bien | Y nghia |
|---|---|
| `DATABASE_URL` | Chuoi ket noi khi huan luyen (nen dung tai khoan chi-doc) |
| `AI_SERVICE_TOKEN` | Token noi bo; Spring gui trong `X-Internal-Token`. Thieu thi service khong khoi dong |
| `AI_ARTIFACT_DIR` | Thu muc chua artifacts (mac dinh `ai-service/artifacts`) |

## API
- `GET /health` — trang thai va model dang nap (khong can token).
- `POST /v1/models/load` `{modelType, modelVersion, checksum?}` — nap artifact; kiem tra checksum va schema dac trung.
- `POST /v1/rank-partners` `{requesterId, candidates:[{userId, features}]}` (toi da 200) -> `totalScore` 0-100, `confidence` 0-1, `reasons`, `modelVersion`.
- `POST /v1/predict-balance` `{options:[{optionId, features}]}` -> `team1WinProbability`, `balance`.

## Quy uoc du lieu
- Dac trung va thu tu nam o `courtly_ai/features.py`; luu vao `metadata.json` va `ai_models.feature_schema_json`. Thieu gia tri thi de trong, khong dien trung binh.
- Chia theo thoi gian (70/15/15), nhan tu ket qua tran trong 60 ngay sau thoi diem T, dac trung chi tu cac tran truoc T.
- Nguoi choi seed duoc bo sung ho so (khu vuc, lich ranh, phong cach) theo quy tac co dinh o `data/bwf-to-seed.py`; chua phai hanh vi that.
