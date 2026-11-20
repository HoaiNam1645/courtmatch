"""Thu nghiem nhieu kich ban du lieu cho model xep hang doi tac de biet da toi uu chua (seed, sieu tham so, so mau am, cua so nhan,
bo bot dac trung, it du lieu, nguoi choi chua tung thay, dich chuyen phan phoi, nhieu va thieu gia tri luc kiem thu).

Chay: uv run python -m experiments.partner_scenarios [ten_kich_ban ...]   (mac dinh: tat ca)
Moi dong ket qua la mot JSON: {"scenario", "variant", "model", "baseline", "skill_only", ...} (NDCG@10 tren tap kiem thu theo thoi gian).
"""

from __future__ import annotations

import json
import random
import sys
import time

import numpy as np
from catboost import CatBoostRanker, Pool

from courtly_ai import data as data_mod, dataset, evaluate
from courtly_ai.features import PARTNER_FEATURES

BASE = {"loss_function": "YetiRankPairwise", "eval_metric": "NDCG:top=10", "iterations": 800, "learning_rate": 0.05, "depth": 6,
        "l2_leaf_reg": 3.0, "early_stopping_rounds": 50, "thread_count": 4, "verbose": False, "allow_writing_files": False}
DATA = None


def get_data():
    global DATA
    if DATA is None:
        DATA = data_mod.load()
    return DATA


def matrix(groups, columns=None, eligible_only=False):
    x, y, gid, chosen = dataset.partner_matrix(groups, eligible_only=eligible_only)
    if columns is not None:
        x = x[:, columns]
    return x, y, gid, chosen


def train(splits, params=None, columns=None, seed=42, train_groups=None):
    params = {**BASE, **(params or {})}
    names = [PARTNER_FEATURES[i] for i in columns] if columns is not None else PARTNER_FEATURES
    tr = train_groups if train_groups is not None else splits["train"]
    xtr, ytr, gtr, _ = matrix(tr, columns, eligible_only=True)
    xva, yva, gva, _ = matrix(splits["valid"], columns, eligible_only=True)
    model = CatBoostRanker(random_seed=seed, **params)
    model.fit(Pool(xtr, ytr, group_id=gtr, feature_names=names), eval_set=Pool(xva, yva, group_id=gva, feature_names=names), use_best_model=True)
    return model


def score_rows(model, groups, columns=None, transform=None):
    flat = [r for g in groups for r in g.rows]
    x = np.array([[r.values.get(name, float("nan")) if r.values.get(name) is not None else float("nan") for name in PARTNER_FEATURES] for r in flat], dtype=float)
    if transform is not None:
        x = transform(x)
    if columns is not None:
        x = x[:, columns]
    pred = model.predict(x)
    return {id(r): float(p) for r, p in zip(flat, pred)}


def report(scenario, variant, model, groups, columns=None, transform=None, extra=None):
    scores = score_rows(model, groups, columns, transform)
    m = evaluate.evaluate_ranking(groups, lambda r: scores[id(r)])
    b = evaluate.evaluate_ranking(groups, lambda r: r.baseline)
    s = evaluate.evaluate_ranking(groups, lambda r: r.skill_only)
    row = {"scenario": scenario, "variant": variant, "groups": m["overall"]["groups"],
           "model": m["overall"]["ndcg@10"], "model_ci": m["overall"]["ndcg@10_ci95"], "baseline": b["overall"]["ndcg@10"],
           "skill_only": s["overall"]["ndcg@10"], "model_p@10": m["overall"]["precision@10"], "model_r@10": m["overall"]["recall@10"],
           "newbie": m.get("newbie", {}).get("ndcg@10"), "newbie_groups": m.get("newbie", {}).get("groups"),
           "single": m.get("single", {}).get("ndcg@10"), "double": m.get("double", {}).get("ndcg@10")}
    if extra:
        row.update(extra)
    print(json.dumps(row, ensure_ascii=False))
    sys.stdout.flush()
    return row


def build(seed=42):
    groups = dataset.build_partner_groups(get_data(), seed)
    return groups, dataset.split_by_time(groups)


def seeds():
    for seed in (1, 2, 3, 4, 5):
        groups, splits = build(seed)
        model = train(splits, seed=seed)
        report("seeds", f"seed={seed}", model, splits["test"])


def hyperparams():
    groups, splits = build()
    for label, params in [("depth4", {"depth": 4}), ("depth8", {"depth": 8}), ("lr0.1", {"learning_rate": 0.1}), ("lr0.03", {"learning_rate": 0.03, "iterations": 1500}),
                          ("l2=10", {"l2_leaf_reg": 10.0}), ("YetiRank", {"loss_function": "YetiRank"}), ("PairLogit", {"loss_function": "PairLogit", "eval_metric": "PairAccuracy"}),
                          ("QueryRMSE", {"loss_function": "QueryRMSE", "eval_metric": "NDCG:top=10"}), ("YetiRank+depth4", {"loss_function": "YetiRank", "depth": 4})]:
        try:
            report("hyperparams", label, train(splits, params), splits["test"])
        except Exception as error:  # mot cau hinh khong hop le khong chan cac cau hinh khac
            print(json.dumps({"scenario": "hyperparams", "variant": label, "error": str(error)[:120]}))


def negatives():
    for k in (5, 10, 20, 50):
        dataset.NEG_PER_GROUP = k
        groups, splits = build()
        report("negatives", f"neg={k}", train(splits), splits["test"])
    dataset.NEG_PER_GROUP = 20


def window():
    for days in (30, 60, 90):
        dataset.WINDOW_DAYS = days
        groups, splits = build()
        report("window", f"window={days}d", train(splits), splits["test"])
    dataset.WINDOW_DAYS = 60


def ablation():
    groups, splits = build()
    idx = {name: i for i, name in enumerate(PARTNER_FEATURES)}
    drop = {"khong same_play_type": ["same_play_type"],
            "khong tuong thich (vi tri/gio/phong cach)": ["location_score", "time_score", "style_score", "distance_km"],
            "khong ho so seed (vi tri/gio/phong cach/loai tran)": ["location_score", "time_score", "style_score", "distance_km", "same_play_type"],
            "khong phong do (win_rate/stability/recency/point_diff)": ["r_win_rate", "c_win_rate", "r_stability", "c_stability", "r_recency_days", "c_recency_days", "r_avg_point_diff", "c_avg_point_diff"],
            "chi Elo + chenh Elo": [n for n in PARTNER_FEATURES if n not in ("r_rating", "c_rating", "rating_gap", "skill_similarity")]}
    for label, names in drop.items():
        columns = [i for n, i in idx.items() if n not in names]
        report("ablation", label, train(splits, columns=columns), splits["test"], columns=columns)
    report("ablation", "day du dac trung", train(splits), splits["test"])


def scarcity():
    groups, splits = build()
    for fraction in (0.1, 0.25, 0.5, 1.0):
        tr = splits["train"][-max(10, int(len(splits["train"]) * fraction)):]
        report("scarcity", f"{int(fraction * 100)}% du lieu huan luyen ({len(tr)} nhom)", train(splits, train_groups=tr), splits["test"])


def unseen_players():
    groups, splits = build()
    rng = random.Random(7)
    players = sorted({g.requester for g in groups})
    held = set(rng.sample(players, len(players) // 5))
    tr = [g for g in splits["train"] if g.requester not in held]
    for g in tr:
        g.rows = [r for r in g.rows if r.candidate not in held]
    test_unseen = [g for g in splits["test"] if g.requester in held]
    test_seen = [g for g in splits["test"] if g.requester not in held]
    model = train(splits, train_groups=[g for g in tr if g.rows])
    report("unseen_players", "nguoi tim da thay luc huan luyen", model, test_seen)
    report("unseen_players", "nguoi tim CHUA tung thay (20% nguoi bi giu lai)", model, test_unseen)


def shift():
    groups, splits = build()
    for train_seg, test_seg in (("single", "double"), ("double", "single")):
        tr = [g for g in splits["train"] if g.segment == train_seg]
        te = [g for g in splits["test"] if g.segment == test_seg]
        report("shift", f"huan luyen {train_seg} -> kiem thu {test_seg}", train(splits, train_groups=tr), te)
    mid = len(splits["train"]) // 2
    report("shift", "huan luyen chi nua dau (xa thoi diem kiem thu)", train(splits, train_groups=splits["train"][:mid]), splits["test"])


def robustness():
    groups, splits = build()
    model = train(splits)
    rng = np.random.default_rng(3)
    names = PARTNER_FEATURES

    def missing(fraction):
        def apply(x):
            mask = rng.random(x.shape) < fraction
            out = x.copy()
            out[mask] = np.nan
            return out
        return apply

    def elo_noise(sigma):
        def apply(x):
            out = x.copy()
            for col in (names.index("r_rating"), names.index("c_rating")):
                out[:, col] = out[:, col] + rng.normal(0, sigma, size=len(out))
            out[:, names.index("rating_gap")] = np.abs(out[:, names.index("r_rating")] - out[:, names.index("c_rating")])
            return out
        return apply

    report("robustness", "du lieu sach", model, splits["test"])
    for f in (0.1, 0.3, 0.5):
        report("robustness", f"{int(f * 100)}% gia tri bi mat luc kiem thu", model, splits["test"], transform=missing(f))
    for s in (25, 50, 100):
        report("robustness", f"nhieu Elo ±{s}", model, splits["test"], transform=elo_noise(s))


def pool_size():
    groups, splits = build()
    model = train(splits)
    for keep in (5, 10, 20, None):
        test = []
        for g in splits["test"]:
            pos = [r for r in g.rows if r.label > 0]
            neg = [r for r in g.rows if r.label == 0]
            rows = pos + (neg[:keep] if keep is not None else neg)
            test.append(dataset.Group(g.id, g.requester, g.at, g.segment, g.newbie, g.eligible, rows))
        report("pool_size", f"{keep if keep is not None else 20} ung vien am moi nhom", model, test)


def coldstart():
    """Nguoi tim moi (duoi 5 tran): khong co nhom nao trong tap kiem thu theo thoi gian, nen danh gia rieng.
    Huan luyen tren nhom cua nguoi tim co tu 5 tran tro len (70% nguoi choi), kiem thu tren nhom cua 30% nguoi con lai luc ho con duoi 5 tran."""
    groups, splits = build()
    rng = random.Random(11)
    players = sorted({g.requester for g in groups})
    held = set(rng.sample(players, int(len(players) * 0.3)))
    train_groups = [g for g in groups if g.requester not in held and not g.newbie]
    cold = [g for g in groups if g.requester in held and g.newbie]
    print(json.dumps({"scenario": "coldstart", "note": "so nhom nguoi tim moi", "all_newbie_groups": sum(1 for g in groups if g.newbie), "cold_test_groups": len(cold)}))
    cut = int(len(train_groups) * 0.85)
    model = train({"train": train_groups[:cut], "valid": train_groups[cut:]}, train_groups=train_groups[:cut])
    if cold:
        report("coldstart", "nguoi tim duoi 5 tran (nguoi choi chua tung thay)", model, cold)
    warm = [g for g in groups if g.requester in held and not g.newbie]
    report("coldstart", "doi chung: nguoi tim tu 5 tran tro len (cung nhom nguoi choi)", model, warm)


def masking():
    """Do ben khi thieu gia tri CHO PHEP thieu (nguoi moi chua co on dinh/phong do, ho so thieu vi tri/lich): huan luyen thuong
    vs huan luyen co che ngau nhien che dac trung (dropout), kiem thu che 0/30/60% cac dac trung cho phep thieu."""
    from courtly_ai.features import NAN_ALLOWED
    groups, splits = build()
    allowed = [PARTNER_FEATURES.index(n) for n in NAN_ALLOWED["partner"]]
    rng = np.random.default_rng(5)
    xtr, ytr, gtr, _ = matrix(splits["train"], eligible_only=True)
    xva, yva, gva, _ = matrix(splits["valid"], eligible_only=True)

    def masked(x, rate):
        out = x.copy()
        for col in allowed:
            out[rng.random(len(out)) < rate, col] = np.nan
        return out

    def fit(x, y, g, extra=None):
        params = {**BASE}
        model = CatBoostRanker(random_seed=42, **params)
        model.fit(Pool(x, y, group_id=g, feature_names=PARTNER_FEATURES), eval_set=Pool(xva, yva, group_id=gva, feature_names=PARTNER_FEATURES), use_best_model=True)
        return model

    plain = fit(xtr, ytr, gtr)
    big_g = gtr.max() + 1
    aug_x = np.vstack([xtr, masked(xtr, 0.3), masked(xtr, 0.6)])
    aug_y = np.concatenate([ytr, ytr, ytr])
    aug_g = np.concatenate([gtr, gtr + big_g, gtr + 2 * big_g])
    robust = fit(aug_x, aug_y, aug_g)
    for rate in (0.0, 0.3, 0.6, 1.0):
        tf = (lambda x, r=rate: masked(x, r)) if rate else None
        report("masking", f"huan luyen thuong, kiem thu che {int(rate * 100)}%", plain, splits["test"], transform=tf)
        report("masking", f"huan luyen co dropout, kiem thu che {int(rate * 100)}%", robust, splits["test"], transform=tf)


SCENARIOS = {"masking": masking, "coldstart": coldstart, "seeds": seeds, "hyperparams": hyperparams, "negatives": negatives, "window": window, "ablation": ablation, "scarcity": scarcity,
             "unseen_players": unseen_players, "shift": shift, "robustness": robustness, "pool_size": pool_size}

if __name__ == "__main__":
    wanted = sys.argv[1:] or list(SCENARIOS)
    for name in wanted:
        started = time.time()
        SCENARIOS[name]()
        print(json.dumps({"scenario": name, "done_seconds": round(time.time() - started)}))
        sys.stdout.flush()
