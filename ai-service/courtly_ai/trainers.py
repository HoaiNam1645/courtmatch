"""Huan luyen CatBoostRanker (doi tac) va CatBoostClassifier (chia doi), danh gia so voi baseline, luu artifact (2.2.13 - 2.2.16)."""

from __future__ import annotations

import hashlib
import json
import math
import platform
from datetime import datetime

import catboost
import numpy as np
from catboost import CatBoostClassifier, CatBoostRanker, Pool

from . import config, dataset, evaluate, registry
from .data import Dataset, load
from .features import NAN_ALLOWED, PARTNER_FEATURES, TEAM_FEATURES, schema_document
from .pairs import elo_win_probability, swap_teams, vector

NDCG_MARGIN = 0.01  # model phai hon baseline it nhat 1 diem phan tram NDCG@10 (2.2.15)
TEAM_LOGLOSS_MARGIN = 0.002

PARTNER_PARAMS = {
    "loss_function": "YetiRankPairwise", "eval_metric": "NDCG:top=10", "iterations": 800, "learning_rate": 0.05,
    "depth": 6, "l2_leaf_reg": 3.0, "early_stopping_rounds": 50, "thread_count": 1, "verbose": False,
    "allow_writing_files": False,
}
TEAM_PARAMS = {
    "loss_function": "Logloss", "eval_metric": "Logloss", "iterations": 800, "learning_rate": 0.05, "depth": 5,
    "l2_leaf_reg": 3.0, "early_stopping_rounds": 50, "thread_count": 1, "verbose": False,
    "allow_writing_files": False,
}

LABEL_DEFINITION = (
    "Muc 0-4 tu ket qua THAT cua cac tran giua hai nguoi trong 60 ngay sau thoi diem T: doi thu sat nut (chenh diem nho, du 3 set) "
    "muc 4, mot chieu muc 1; dong doi thang muc 3, thua muc 2; ung vien hop le chua tung gap la mau am (muc 0)."
)
DATA_NOTE = (
    "Ho so khu vuc, lich ranh, phong cach cua nguoi choi seed duoc sinh theo quy tac co dinh (data/bwf-to-seed.py), chua phai "
    "hanh vi nguoi dung that; nhan lay tu ket qua tran. Loai tran ua thich cua nguoi seed suy tu loai tran ho choi nhieu nhat nen "
    "dac trung same_play_type co phan vong (circular) va chiem trong so lon; se huan luyen lai khi co du phan hoi that (2.2.19)."
)


def _params_hash(params: dict, seed: int) -> str:
    return hashlib.sha256(json.dumps({"p": params, "seed": seed}, sort_keys=True).encode()).hexdigest()


def _range(groups) -> dict:
    if not groups:
        return {"from": None, "to": None, "groups": 0}
    times = sorted(g.at for g in groups)
    return {"from": times[0].date().isoformat(), "to": times[-1].date().isoformat(), "groups": len(groups)}


def _importance(model, names: list[str]) -> dict:
    values = model.get_feature_importance(type="PredictionValuesChange")
    ranked = sorted(zip(names, (float(v) for v in values)), key=lambda item: -item[1])
    return {"importance": [{"feature": n, "value": round(v, 4)} for n, v in ranked]}


def _versions() -> dict:
    return {"catboost": catboost.__version__, "numpy": np.__version__, "python": platform.python_version()}


# ---------------------------------------------------------------- doi tac
def train_partner(data: Dataset | None = None, seed: int = config.RANDOM_SEED, params: dict | None = None,
                  save: bool = True) -> dict:
    data = data or load()
    groups = dataset.build_partner_groups(data, seed)
    if len(groups) < 60:
        raise SystemExit(f"Chi co {len(groups)} nhom xep hang, khong du de huan luyen (can it nhat 60).")
    splits = dataset.split_by_time(groups)
    params = {**PARTNER_PARAMS, **(params or {})}

    xtr, ytr, gtr, train_groups = dataset.partner_matrix(splits["train"], eligible_only=True)
    xva, yva, gva, valid_groups = dataset.partner_matrix(splits["valid"], eligible_only=True)
    model = CatBoostRanker(random_seed=seed, **params)
    model.fit(Pool(xtr, ytr, group_id=gtr, feature_names=PARTNER_FEATURES),
              eval_set=Pool(xva, yva, group_id=gva, feature_names=PARTNER_FEATURES), use_best_model=True)

    def ai_score(row):
        return float(model.predict(np.array([vector(row.values, PARTNER_FEATURES)]))[0])

    test = splits["test"]
    # Du doan theo lo cho nhanh: gan diem model vao tung dong test.
    flat = [r for g in test for r in g.rows]
    scores = model.predict(np.array([vector(r.values, PARTNER_FEATURES) for r in flat]))
    score_by_row = {id(r): float(s) for r, s in zip(flat, scores)}
    metrics = {
        "model": evaluate.evaluate_ranking(test, lambda r: score_by_row[id(r)]),
        "baseline": evaluate.evaluate_ranking(test, lambda r: r.baseline),
        "skill_only": evaluate.evaluate_ranking(test, lambda r: r.skill_only),
    }
    gate = partner_gate(metrics)
    rule_check = _permutation_check(test, score_by_row)
    gate["filter_rule_check"] = rule_check
    gate["passes"] = gate["passes"] and rule_check["ok"]

    valid_raw = model.predict(xva)
    calibration = {"mean": float(np.mean(valid_raw)), "std": float(np.std(valid_raw)) or 1.0}
    version = registry.new_version("partner", _params_hash(params, seed))
    metadata = {
        "kind": "partner", "modelType": "catboost_ranker", "schema": schema_document("partner"),
        "seed": seed, "parameters": {k: v for k, v in params.items()}, "bestIteration": int(model.get_best_iteration() or 0),
        "labelDefinition": LABEL_DEFINITION, "dataNote": DATA_NOTE,
        "data": {"train": _range(train_groups), "valid": _range(valid_groups), "test": _range(test),
                 "trainRows": int(len(ytr)), "validRows": int(len(yva)), "requestMinutes": dataset.REQUEST_MINUTES,
                 "windowDays": dataset.WINDOW_DAYS, "negativesPerGroup": dataset.NEG_PER_GROUP,
                 "minTrainMatches": dataset.MIN_TRAIN_MATCHES},
        "metrics": metrics, "gate": gate, "scoreCalibration": calibration, "versions": _versions(),
        "nanAllowed": sorted(NAN_ALLOWED["partner"]),
    }
    if save:
        registry.save(version, model, metadata, _importance(model, PARTNER_FEATURES))
    return {"version": version, "metadata": metadata}


def partner_gate(metrics: dict) -> dict:
    model, base = metrics["model"], metrics["baseline"]
    reasons = []
    overall_delta = model["overall"]["ndcg@10"] - base["overall"]["ndcg@10"]
    if overall_delta < NDCG_MARGIN:
        reasons.append(f"NDCG@10 chua hon baseline it nhat {NDCG_MARGIN} (chenh {overall_delta:.4f})")
    for segment in ("newbie", "single", "double"):
        if segment in model and segment in base and model[segment].get("enough_data"):
            if model[segment]["ndcg@10"] < base[segment]["ndcg@10"]:
                reasons.append(f"Nhom {segment} kem baseline ({model[segment]['ndcg@10']} < {base[segment]['ndcg@10']})")
    return {"passes": not reasons, "reasons": reasons, "ndcg10Delta": round(overall_delta, 4), "margin": NDCG_MARGIN}


def _permutation_check(test_groups, score_by_row) -> dict:
    """Luat loc (2.2.15/2.2.18): xep hang chi hoan vi dung tap ung vien da loc, khong them/bot ung vien."""
    checked = 0
    for g in test_groups[:50]:
        order = sorted(range(len(g.rows)), key=lambda i: -score_by_row[id(g.rows[i])])
        if sorted(order) != list(range(len(g.rows))):
            return {"ok": False, "checked": checked}
        checked += 1
    return {"ok": True, "checked": checked}


# ---------------------------------------------------------------- chia doi
def _logit(p: float) -> float:
    p = min(1 - 1e-6, max(1e-6, p))
    return math.log(p / (1 - p))


def _elo_prior(x: np.ndarray) -> np.ndarray:
    """Logit cua xac suat thang theo Elo tu cot rating_diff: model hoc phan DU tren nen Elo (init raw score), nen it nhat bang baseline."""
    col = TEAM_FEATURES.index("rating_diff")
    return np.array([_logit(elo_win_probability(v)) for v in x[:, col]])


def train_team(data: Dataset | None = None, seed: int = config.RANDOM_SEED, params: dict | None = None,
               save: bool = True) -> dict:
    data = data or load()
    rows = dataset.build_team_rows(data)
    if len(rows) < 300:
        raise SystemExit(f"Chi co {len(rows)} tran doi hop le, khong du de huan luyen (can it nhat 300).")
    splits = dataset.split_team_rows(rows)
    params = {**TEAM_PARAMS, **(params or {})}
    xtr, ytr = dataset.team_matrix([r for r in splits["train"] if min(r.values["t1_min_matches"], r.values["t2_min_matches"]) >= 1], augment=True)
    xva, yva = dataset.team_matrix(splits["valid"], augment=True)
    model = CatBoostClassifier(random_seed=seed, **params)
    model.fit(Pool(xtr, ytr, feature_names=TEAM_FEATURES, baseline=_elo_prior(xtr)),
              eval_set=Pool(xva, yva, feature_names=TEAM_FEATURES, baseline=_elo_prior(xva)), use_best_model=True)

    def proba(x: np.ndarray) -> np.ndarray:
        raw = model.predict(Pool(x, feature_names=TEAM_FEATURES), prediction_type="RawFormulaVal") + _elo_prior(x)
        return 1.0 / (1.0 + np.exp(-raw))

    def predict(values_list: list[dict]) -> list[float]:
        direct = proba(np.array([vector(v, TEAM_FEATURES) for v in values_list]))
        swapped = proba(np.array([vector(swap_teams(v), TEAM_FEATURES) for v in values_list]))
        return [float((a + (1 - b)) / 2) for a, b in zip(direct, swapped)]  # doi xung: P(doi 1) = (P + 1 - P(doi cho)) / 2

    test = splits["test"]
    p_model = predict([r.values for r in test])
    p_base = [elo_win_probability(r.values["rating_diff"]) for r in test]
    y = [r.label for r in test]
    metrics = {"model": {"overall": evaluate.binary_metrics(y, p_model)}, "baseline": {"overall": evaluate.binary_metrics(y, p_base)}}
    for segment in sorted({r.segment for r in test}):
        idx = [i for i, r in enumerate(test) if r.segment == segment]
        for name, probs in (("model", p_model), ("baseline", p_base)):
            m = evaluate.binary_metrics([y[i] for i in idx], [probs[i] for i in idx])
            m["enough_data"] = len(idx) >= evaluate.MIN_GROUPS
            metrics[name][segment] = m
    gate = team_gate(metrics)
    valid_p = predict([r.values for r in splits["valid"]])
    spread = float(np.std([abs(2 * p - 1) for p in valid_p])) or 1.0
    version = registry.new_version("team", _params_hash(params, seed))
    metadata = {
        "kind": "team", "modelType": "double_team_ranker", "schema": schema_document("team"), "seed": seed,
        "parameters": params, "bestIteration": int(model.get_best_iteration() or 0),
        "labelDefinition": "1 neu doi 1 thang tran; mau huan luyen duoc doi xung (doi cho hai doi va dao nhan).",
        "dataNote": DATA_NOTE,
        "data": {"train": {"rows": len(splits["train"])}, "valid": {"rows": len(splits["valid"])}, "test": {"rows": len(test),
                 "from": test[0].at.date().isoformat(), "to": test[-1].at.date().isoformat()}},
        "metrics": metrics, "gate": gate, "scoreCalibration": {"spread": spread}, "usesEloPrior": True, "versions": _versions(),
        "nanAllowed": sorted(NAN_ALLOWED["team"]),
    }
    if save:
        registry.save(version, model, metadata, _importance(model, TEAM_FEATURES))
    return {"version": version, "metadata": metadata}


def team_gate(metrics: dict) -> dict:
    model, base = metrics["model"], metrics["baseline"]
    reasons = []
    delta = base["overall"]["log_loss"] - model["overall"]["log_loss"]
    if delta < TEAM_LOGLOSS_MARGIN:
        reasons.append(f"Log-loss chua tot hon baseline it nhat {TEAM_LOGLOSS_MARGIN} (cai thien {delta:.4f})")
    if model["overall"]["accuracy"] < base["overall"]["accuracy"] - 0.005:
        reasons.append("Ty le doan dung doi thang thap hon baseline")
    for segment, m in model.items():
        if segment != "overall" and m.get("enough_data") and m["log_loss"] > base[segment]["log_loss"] + 0.01:
            reasons.append(f"Nhom {segment} kem baseline ve log-loss")
    return {"passes": not reasons, "reasons": reasons, "logLossGain": round(delta, 4), "margin": TEAM_LOGLOSS_MARGIN}
