"""AI service FastAPI (2.2.17): xep hang doi tac, du doan can bang doi, health, nap model.

Xac thuc noi bo bang tieu de X-Internal-Token (AI_SERVICE_TOKEN); mac dinh chi lang nghe 127.0.0.1. Thieu token thi khong khoi dong.
"""

from __future__ import annotations

import hmac
import threading
import time
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from . import config, inference

KIND_BY_TYPE = {"catboost_ranker": "partner", "catboost_team_ranker": "team"}


class Candidate(BaseModel):
    userId: str = Field(min_length=1, max_length=64)
    features: dict[str, float | None]


class RankRequest(BaseModel):
    requesterId: str = Field(min_length=1, max_length=64)
    candidates: list[Candidate] = Field(min_length=1, max_length=inference.MAX_CANDIDATES)


class Option(BaseModel):
    optionId: str = Field(min_length=1, max_length=64)
    features: dict[str, float | None]


class BalanceRequest(BaseModel):
    options: list[Option] = Field(min_length=1, max_length=10)


class LoadRequest(BaseModel):
    modelType: str
    modelVersion: str = Field(pattern=r"^[A-Za-z0-9._-]{1,80}$")  # chi ten thu muc hop le, khong cho duong dan tuy y
    checksum: str | None = None


def create_app(token: str | None) -> FastAPI:
    if not token:
        raise RuntimeError("Thieu AI_SERVICE_TOKEN: AI service khong khoi dong khi chua cau hinh xac thuc noi bo.")
    app = FastAPI(title="Courtly AI service", docs_url=None, redoc_url=None)
    store: dict[str, inference.Loaded] = {}
    lock = threading.Lock()

    def auth(x_internal_token: Annotated[str | None, Header()] = None) -> None:
        if x_internal_token is None or not hmac.compare_digest(x_internal_token.encode(), token.encode()):
            raise HTTPException(status_code=401, detail="Xac thuc noi bo khong hop le")

    def current(kind: str) -> inference.Loaded:
        loaded = store.get(kind)
        if loaded is None:
            raise HTTPException(status_code=503, detail=f"Chua nap model {kind}")
        return loaded

    @app.get("/health")
    def health():
        with lock:
            models = {kind: {"modelVersion": m.version, "checksum": m.checksum, "loadedAt": m.loaded_at.isoformat()}
                      for kind, m in store.items()}
        return {"status": "ok", "models": models}

    @app.post("/v1/models/load", dependencies=[Depends(auth)])
    def load_model(request: LoadRequest):
        kind = KIND_BY_TYPE.get(request.modelType)
        if kind is None:
            raise HTTPException(status_code=422, detail="Loai model khong hop le")
        try:
            loaded = inference.load_model(kind, request.modelVersion, request.checksum)
        except FileNotFoundError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error
        except inference.SchemaError as error:
            raise HTTPException(status_code=409, detail=str(error)) from error
        with lock:
            store[kind] = loaded
        return {"modelVersion": loaded.version, "checksum": loaded.checksum, "kind": kind}

    @app.post("/v1/rank-partners", dependencies=[Depends(auth)])
    def rank(request: RankRequest):
        loaded = current("partner")
        started = time.perf_counter()
        try:
            results = inference.rank_partners(loaded, [c.model_dump() for c in request.candidates])
        except inference.SchemaError as error:
            raise HTTPException(status_code=422, detail=str(error)) from error
        return {"modelVersion": loaded.version, "scoringSource": "ai", "results": results,
                "durationMs": round((time.perf_counter() - started) * 1000, 2)}

    @app.post("/v1/predict-balance", dependencies=[Depends(auth)])
    def balance(request: BalanceRequest):
        loaded = current("team")
        try:
            results = inference.predict_balance(loaded, [o.model_dump() for o in request.options])
        except inference.SchemaError as error:
            raise HTTPException(status_code=422, detail=str(error)) from error
        except KeyError as error:
            raise HTTPException(status_code=422, detail=f"Thieu dac trung: {error}") from error
        return {"modelVersion": loaded.version, "scoringSource": "ai", "results": results}

    return app


def _default_app() -> FastAPI:
    return create_app(config.service_token())


app = None
try:  # khi import de chay uvicorn courtly_ai.service:app; test tu goi create_app
    import os

    if os.environ.get("AI_SERVICE_TOKEN"):
        app = _default_app()
except RuntimeError:
    app = None
