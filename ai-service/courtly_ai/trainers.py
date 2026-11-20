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


MASK_RATE = 0.2  # ty le che ngau nhien cac dac trung CHO PHEP thieu o mot ban sao cua tap huan luyen (xem experiments: ben hon khi nguoi moi thieu du lieu)


def _mask_augment(x, y, g, seed):
    """Them mot ban sao tap huan luyen trong do 20% gia tri cua cac dac trung cho phep thieu (stability, recency, vi tri, gio...)
    bi che thanh NaN: nguoi moi/ho so thieu thuong gap o thuc te nhung it trong du lieu seed. Do tren kiem thu: -0,011 NDCG@10 khi du lieu day du
    nhung +0,012 khi thieu 30% va +0,03 khi thieu het cac dac trung do."""
    rng = np.random.default_rng(seed)
    copy = x.copy()
    for name in NAN_ALLOWED["partner"]:
        col = PARTNER_FEATURES.index(name)
        copy[rng.random(len(copy)) < MASK_RATE, col] = np.nan
    return np.vstack([x, copy]), np.concatenate([y, y]), np.concatenate([g, g + (g.max() + 1)])


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
    rows_before = int(len(ytr))
    xtr, ytr, gtr = _mask_augment(xtr, ytr, gtr, seed)
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
                 "trainRows": rows_before, "maskAugment": MASK_RATE, "trainRowsAugmented": int(len(ytr)), "validRows": int(len(yva)), "requestMinutes": dataset.REQUEST_MINUTES,
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
    cv = team_cv(rows, params, seed)
    gate = team_gate(metrics, cv)
    gate["cv"] = cv
    valid_p = predict([r.values for r in splits["valid"]])
    spread = float(np.std([abs(2 * p - 1) for p in valid_p])) or 1.0
    version = registry.new_version("team", _params_hash(params, seed))
    metadata = {
        "kind": "team", "modelType": "catboost_team_ranker", "schema": schema_document("team"), "seed": seed,
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


def team_cv(rows, params: dict, seed: int, folds=(0.5, 0.6, 0.7, 0.8, 0.9), step=0.1) -> dict:
    """Rolling-origin CV (nhieu moc thoi gian) + khoang tin cay bootstrap cua muc cai thien log-loss so voi Elo: mot tap test vai tram tran
    qua nhieu nhieu de ket luan 'tot hon baseline' (2.2.15)."""
    ordered = sorted(rows, key=lambda r: r.at)
    n = len(ordered)
    gains: list[float] = []
    for f in folds:
        a, b = int(n * f), int(n * min(1.0, f + step))
        train_rows, test_rows = ordered[:a], ordered[a:b]
        if len(train_rows) < 200 or not test_rows:
            continue
        x, y = dataset.team_matrix(train_rows, augment=True)
        cut = int(len(y) * 0.85) // 2 * 2
        model = CatBoostClassifier(random_seed=seed, **params)
        model.fit(Pool(x[:cut], y[:cut], feature_names=TEAM_FEATURES, baseline=_elo_prior(x[:cut])),
                  eval_set=Pool(x[cut:], y[cut:], feature_names=TEAM_FEATURES, baseline=_elo_prior(x[cut:])), use_best_model=True)

        def proba(values):
            xx = np.array([vector(values, TEAM_FEATURES)])
            raw = model.predict(Pool(xx, feature_names=TEAM_FEATURES), prediction_type="RawFormulaVal") + _elo_prior(xx)
            return float(1 / (1 + np.exp(-raw[0])))

        for r in test_rows:
            p = (proba(r.values) + 1 - proba(swap_teams(r.values))) / 2
            e = elo_win_probability(r.values["rating_diff"])
            ll = lambda q: -(r.label * math.log(max(1e-9, q)) + (1 - r.label) * math.log(max(1e-9, 1 - q)))
            gains.append(ll(e) - ll(p))
    if not gains:
        return {"samples": 0, "gain": None, "gain_ci95": None}
    rng = np.random.default_rng(seed)
    arr = np.array(gains)
    boots = np.sort([rng.choice(arr, size=len(arr)).mean() for _ in range(500)])
    return {"samples": len(gains), "gain": round(float(arr.mean()), 4), "gain_ci95": [round(float(boots[12]), 4), round(float(boots[487]), 4)]}


def team_gate(metrics: dict, cv: dict | None = None) -> dict:
    model, base = metrics["model"], metrics["baseline"]
    reasons = []
    if cv is not None and cv.get("gain") is not None:
        if cv["gain"] < TEAM_LOGLOSS_MARGIN or cv["gain_ci95"][0] <= 0:
            reasons.append(f"CV nhieu moc thoi gian chua chung minh tot hon Elo (cai thien {cv['gain']}, khoang tin cay 95% {cv['gain_ci95']}, can can duoi > 0)")
    delta = base["overall"]["log_loss"] - model["overall"]["log_loss"]
    if delta < TEAM_LOGLOSS_MARGIN:
        reasons.append(f"Log-loss chua tot hon baseline it nhat {TEAM_LOGLOSS_MARGIN} (cai thien {delta:.4f})")
    if model["overall"]["accuracy"] < base["overall"]["accuracy"] - 0.005:
        reasons.append("Ty le doan dung doi thang thap hon baseline")
    for segment, m in model.items():
        if segment != "overall" and m.get("enough_data") and m["log_loss"] > base[segment]["log_loss"] + 0.01:
            reasons.append(f"Nhom {segment} kem baseline ve log-loss")
    return {"passes": not reasons, "reasons": reasons, "logLossGain": round(delta, 4), "margin": TEAM_LOGLOSS_MARGIN}
