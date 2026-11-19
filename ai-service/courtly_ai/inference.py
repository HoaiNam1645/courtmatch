"""Suy luan cho hai loai model (2.2.17 - 2.2.18): dung chung ma voi huan luyen de khong lech nhau."""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import datetime, timezone

import numpy as np
from catboost import CatBoostClassifier, CatBoostRanker, Pool

from . import registry
from .features import NAN_ALLOWED, PARTNER_FEATURES, SCHEMAS, TEAM_FEATURES, schema_document
from .pairs import elo_win_probability

MAX_CANDIDATES = 200

# Dac trung -> ly do de hieu (khong lo ten dac trung hay trong so cho nguoi choi; Spring doi ma sang loi tieng Viet).
REASON_GROUPS = {
    "SKILL_BALANCED": {"skill_similarity", "rating_gap", "r_rating", "c_rating"},
    "NEAR_LOCATION": {"location_score", "distance_km"},
    "SAME_TIME": {"time_score"},
    "STYLE_MATCH": {"style_score"},
    "SAME_PLAY_TYPE": {"same_play_type"},
    "GOOD_FORM": {"c_win_rate", "r_win_rate", "c_avg_point_diff", "r_avg_point_diff", "c_stability", "r_stability", "c_recency_days", "r_recency_days"},
    "EXPERIENCED": {"c_matches", "r_matches", "c_provisional", "r_provisional"},
}


class SchemaError(ValueError):
    pass


@dataclass
class Loaded:
    kind: str
    version: str
    checksum: str
    metadata: dict
    model: object
    loaded_at: datetime


def load_model(kind: str, version: str, expected_checksum: str | None = None) -> Loaded:
    metadata = registry.load_metadata(version)
    if metadata.get("kind") != kind:
        raise SchemaError(f"Model {version} khong phai loai {kind}")
    path = registry.artifact_dir(version) / "model.cbm"
    if not path.is_file():
        raise FileNotFoundError(f"Khong co file model cho {version}")
    actual = registry.sha256(path)
    if actual != metadata.get("checksum") or (expected_checksum and actual != expected_checksum):
        raise SchemaError("Checksum file model khong khop metadata")
    if metadata.get("schema") != schema_document(kind):
        raise SchemaError("Schema dac trung cua model khac schema trong ma nguon")
    model = CatBoostRanker() if kind == "partner" else CatBoostClassifier()
    model.load_model(str(path))
    return Loaded(kind, version, actual, metadata, model, datetime.now(timezone.utc))


def _row(kind: str, features: dict) -> list[float]:
    names = SCHEMAS[kind]
    unknown = set(features) - set(names)
    if unknown:
        raise SchemaError(f"Dac trung khong co trong schema: {sorted(unknown)}")
    row = []
    for name in names:
        value = features.get(name)
        if value is None:
            if name not in NAN_ALLOWED[kind]:
                raise SchemaError(f"Thieu dac trung bat buoc: {name}")
            row.append(float("nan"))
        else:
            if isinstance(value, float) and not math.isfinite(value):
                raise SchemaError(f"Dac trung {name} khong hop le")
            row.append(float(value))
    return row


def rank_partners(loaded: Loaded, candidates: list[dict]) -> list[dict]:
    """candidates: [{userId, features}] -> danh sach giu NGUYEN tap ung vien, kem diem 0-100, do tin cay, ly do."""
    if not candidates:
        return []
    if len(candidates) > MAX_CANDIDATES:
        raise SchemaError(f"Toi da {MAX_CANDIDATES} ung vien moi lan")
    x = np.array([_row("partner", c["features"]) for c in candidates], dtype=float)
    raw = loaded.model.predict(x)
    cal = loaded.metadata["scoreCalibration"]
    shap = loaded.model.get_feature_importance(Pool(x, feature_names=PARTNER_FEATURES), type="ShapValues")
    results = []
    for i, candidate in enumerate(candidates):
        score = 100.0 / (1.0 + math.exp(-(float(raw[i]) - cal["mean"]) / cal["std"]))
        results.append({
            "userId": candidate["userId"], "totalScore": round(score, 2), "confidence": _confidence(candidate["features"]),
            "reasons": _reasons(shap[i][:-1]),
        })
    return results


def _confidence(f: dict) -> float:
    """Do tin cay 0-1 theo do day du lieu cua hai nguoi (so tran) va do moi cua ung vien; khong lien quan do lon cua diem."""
    rm, cm = f.get("r_matches"), f.get("c_matches")
    depth = 0.3 if rm is None or cm is None else min(1.0, min(rm, cm) / 20.0)
    recency = f.get("c_recency_days")
    freshness = 0.5 if recency is None else (1.0 if recency <= 30 else max(0.3, 1.0 - (recency - 30) / 300.0))
    return round(min(1.0, max(0.1, 0.7 * depth + 0.3 * freshness)), 4)


def _reasons(contributions) -> list[dict]:
    totals = {}
    for index, name in enumerate(PARTNER_FEATURES):
        for code, members in REASON_GROUPS.items():
            if name in members:
                totals[code] = totals.get(code, 0.0) + float(contributions[index])
    ranked = sorted(((v, c) for c, v in totals.items() if v > 0), reverse=True)[:3]
    return [{"code": code, "weight": round(value, 4)} for value, code in ranked]


def predict_balance(loaded: Loaded, options: list[dict]) -> list[dict]:
    """options: [{optionId, features}] -> xac suat doi 1 thang (doi xung) va do can bang 0-100 (100 = rat can bang)."""
    if not options:
        return []
    results = []
    for option in options:
        row = _row("team", option["features"])
        swapped = dict(option["features"])
        for key in ("rating", "partner_gap", "win_rate", "min_matches", "stability", "pair_compat", "recency_days"):
            swapped[f"t1_{key}"], swapped[f"t2_{key}"] = option["features"].get(f"t2_{key}"), option["features"].get(f"t1_{key}")
        swapped["rating_diff"] = -option["features"]["rating_diff"]
        p = (_team_proba(loaded, row, option["features"]["rating_diff"])
             + 1 - _team_proba(loaded, _row("team", swapped), swapped["rating_diff"])) / 2
        results.append({"optionId": option["optionId"], "team1WinProbability": round(p, 4),
                        "balance": round(100.0 * (1.0 - abs(2.0 * p - 1.0)), 2)})
    return results


def _team_proba(loaded: Loaded, row: list[float], rating_diff: float) -> float:
    prior = math.log(elo_win_probability(rating_diff) / (1 - elo_win_probability(rating_diff)))
    raw = float(loaded.model.predict(Pool(np.array([row]), feature_names=TEAM_FEATURES), prediction_type="RawFormulaVal")[0])
    return 1.0 / (1.0 + math.exp(-(raw + prior)))
