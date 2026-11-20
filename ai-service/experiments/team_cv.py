"""Kiem tra model chia doi bang rolling-origin CV (nhieu moc thoi gian) + khoang tin cay cap doi, thay cho mot tap test 209 mau.

Chay: uv run python -m experiments.team_cv
"""

import json
import math
import random
import sys

import numpy as np
from catboost import CatBoostClassifier, Pool

from courtly_ai import data as data_mod, dataset
from courtly_ai.features import TEAM_FEATURES
from courtly_ai.pairs import elo_win_probability, swap_teams, vector
from courtly_ai.trainers import _elo_prior, _logit


def fit(train_rows, params, seed=42):
    x, y = dataset.team_matrix(train_rows, augment=True)
    cut = int(len(y) * 0.85) // 2 * 2  # giu cap mau doi xung o cung phia
    model = CatBoostClassifier(random_seed=seed, loss_function="Logloss", thread_count=4, verbose=False,
                               allow_writing_files=False, early_stopping_rounds=40, **params)
    model.fit(Pool(x[:cut], y[:cut], feature_names=TEAM_FEATURES, baseline=_elo_prior(x[:cut])),
              eval_set=Pool(x[cut:], y[cut:], feature_names=TEAM_FEATURES, baseline=_elo_prior(x[cut:])), use_best_model=True)
    return model


def proba(model, rows):
    def one(values):
        x = np.array([vector(values, TEAM_FEATURES)])
        raw = model.predict(Pool(x, feature_names=TEAM_FEATURES), prediction_type="RawFormulaVal") + _elo_prior(x)
        return float(1 / (1 + np.exp(-raw[0])))
    return [(one(r.values) + 1 - one(swap_teams(r.values))) / 2 for r in rows]


def logloss(y, p):
    return -(y * math.log(max(1e-9, p)) + (1 - y) * math.log(max(1e-9, 1 - p)))


def run(params, folds=(0.5, 0.6, 0.7, 0.8, 0.9), step=0.1):
    d = data_mod.load()
    rows = sorted(dataset.build_team_rows(d), key=lambda r: r.at)
    n = len(rows)
    diffs, model_ll, base_ll, acc_m, acc_b = [], [], [], 0, 0
    for f in folds:
        a, b = int(n * f), int(n * min(1.0, f + step))
        train, test = rows[:a], rows[a:b]
        model = fit(train, params)
        p = proba(model, test)
        for r, q in zip(test, p):
            e = elo_win_probability(r.values["rating_diff"])
            ml, bl = logloss(r.label, q), logloss(r.label, e)
            model_ll.append(ml)
            base_ll.append(bl)
            diffs.append(bl - ml)  # > 0 : model tot hon Elo
            acc_m += (q > 0.5) == (r.label == 1)
            acc_b += (e > 0.5) == (r.label == 1)
    rng = random.Random(1)
    boots = sorted(sum(rng.choices(diffs, k=len(diffs))) / len(diffs) for _ in range(500))
    return {"params": params, "samples": len(diffs), "model_logloss": round(sum(model_ll) / len(model_ll), 4),
            "elo_logloss": round(sum(base_ll) / len(base_ll), 4), "gain": round(sum(diffs) / len(diffs), 4),
            "gain_ci95": [round(boots[12], 4), round(boots[487], 4)], "acc_model": round(acc_m / len(diffs), 4),
            "acc_elo": round(acc_b / len(diffs), 4)}


if __name__ == "__main__":
    grid = [{"depth": 2, "learning_rate": 0.03, "l2_leaf_reg": 20, "iterations": 400},
            {"depth": 3, "learning_rate": 0.03, "l2_leaf_reg": 10, "iterations": 400},
            {"depth": 4, "learning_rate": 0.02, "l2_leaf_reg": 30, "iterations": 600}]
    for params in grid:
        print(json.dumps(run(params), ensure_ascii=False))
        sys.stdout.flush()
