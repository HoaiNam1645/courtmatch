"""Cau hinh doc tu bien moi truong."""

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ARTIFACT_DIR = Path(os.environ.get("AI_ARTIFACT_DIR", ROOT / "artifacts")).resolve()
DEFAULT_DATABASE_URL = "postgresql://courtly:courtly@localhost:5432/courtly"
RANDOM_SEED = 42


def database_url() -> str:
    return os.environ.get("DATABASE_URL", DEFAULT_DATABASE_URL)


def service_token() -> str | None:
    value = os.environ.get("AI_SERVICE_TOKEN", "").strip()
    return value or None
