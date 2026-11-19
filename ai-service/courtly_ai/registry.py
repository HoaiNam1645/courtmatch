"""Luu artifact + metadata cua model (2.2.13, 2.2.16); file .cbm nam o artifacts/ (khong dua len git)."""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

from . import config


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def artifact_dir(version: str) -> Path:
    return config.ARTIFACT_DIR / version


def new_version(kind: str, parameters_hash: str) -> str:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d")
    prefix = {"partner": "partner-catboost", "team": "team-catboost"}[kind]
    return f"{prefix}-{stamp}-{parameters_hash[:8]}"


def save(version: str, model, metadata: dict, importance: dict) -> Path:
    directory = artifact_dir(version)
    directory.mkdir(parents=True, exist_ok=True)
    model_path = directory / "model.cbm"
    model.save_model(str(model_path))
    metadata = dict(metadata)
    metadata["modelVersion"] = version
    metadata["checksum"] = sha256(model_path)
    metadata["trainedAt"] = datetime.now(timezone.utc).isoformat()
    (directory / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=1), encoding="utf-8")
    (directory / "feature_importance.json").write_text(json.dumps(importance, ensure_ascii=False, indent=1), encoding="utf-8")
    return directory


def load_metadata(version: str) -> dict:
    path = artifact_dir(version) / "metadata.json"
    if not path.is_file():
        raise FileNotFoundError(f"Khong co metadata cho {version}")
    return json.loads(path.read_text(encoding="utf-8"))
