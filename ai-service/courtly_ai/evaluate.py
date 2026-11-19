"""Thuoc do danh gia (2.2.14 - 2.2.15): NDCG@K, Precision@K, Recall@K cho xep hang; log-loss, Brier, accuracy cho chia doi."""

from __future__ import annotations

import math
import random
from collections import defaultdict

RELEVANT_FROM = 2  # nhan tu muc 2 tro len la "lien quan"
KS = (3, 5, 10)
MIN_GROUPS = 30  # duoi nguong nay gan "Chua du du lieu"


def _dcg(labels: list[int], k: int) -> float:
    return sum((2 ** lab - 1) / math.log2(i + 2) for i, lab in enumerate(labels[:k]))


def group_metrics(labels: list[int], scores: list[float]) -> dict[str, float]:
    """labels/scores song song; xep giam dan theo score (hoa thi giu thu tu goc, on dinh)."""
    order = sorted(range(len(labels)), key=lambda i: (-scores[i], i))
    ranked = [labels[i] for i in order]
    ideal = sorted(labels, reverse=True)
    relevant_total = sum(1 for lab in labels if lab >= RELEVANT_FROM)
    out: dict[str, float] = {}
    for k in KS:
        ideal_dcg = _dcg(ideal, k)
        out[f"ndcg@{k}"] = _dcg(ranked, k) / ideal_dcg if ideal_dcg > 0 else 0.0
        hits = sum(1 for lab in ranked[:k] if lab >= RELEVANT_FROM)
        out[f"precision@{k}"] = hits / k
        out[f"recall@{k}"] = hits / relevant_total if relevant_total else 0.0
    return out


def _mean(items: list[dict], key: str) -> float:
    return sum(i[key] for i in items) / len(items) if items else 0.0


def aggregate(per_group: list[dict]) -> dict[str, float]:
    keys = [f"{m}@{k}" for m in ("ndcg", "precision", "recall") for k in KS]
    return {key: round(_mean(per_group, key), 4) for key in keys} | {"groups": len(per_group)}


def bootstrap_ci(per_group: list[dict], key: str = "ndcg@10", samples: int = 300, seed: int = 42) -> tuple[float, float]:
    if len(per_group) < 2:
        return (0.0, 0.0)
    rng = random.Random(seed)
    values = [i[key] for i in per_group]
    means = sorted(sum(rng.choices(values, k=len(values))) / len(values) for _ in range(samples))
    return (round(means[int(0.025 * samples)], 4), round(means[int(0.975 * samples) - 1], 4))


def evaluate_ranking(groups, score_of) -> dict:
    """groups: danh sach Group; score_of(row) -> diem. Tra ve tong the + theo don/doi/nguoi moi + khoang tin cay."""
    per_group = []
    for g in groups:
        metrics = group_metrics([r.label for r in g.rows], [score_of(r) for r in g.rows])
        per_group.append((g, metrics))
    segments: dict[str, list[dict]] = defaultdict(list)
    for g, m in per_group:
        segments[g.segment].append(m)
        segments["newbie" if g.newbie else "experienced"].append(m)
    all_metrics = [m for _, m in per_group]
    result = {"overall": aggregate(all_metrics)}
    result["overall"]["ndcg@10_ci95"] = bootstrap_ci(all_metrics)
    result["overall"]["enough_data"] = len(all_metrics) >= MIN_GROUPS
    for name, items in segments.items():
        agg = aggregate(items)
        agg["enough_data"] = len(items) >= MIN_GROUPS
        result[name] = agg
    return result


def binary_metrics(y_true: list[int], p: list[float]) -> dict[str, float]:
    n = len(y_true)
    eps = 1e-12
    logloss = -sum(y * math.log(min(1 - eps, max(eps, q))) + (1 - y) * math.log(min(1 - eps, max(eps, 1 - q)))
                   for y, q in zip(y_true, p)) / n
    brier = sum((q - y) ** 2 for y, q in zip(y_true, p)) / n
    accuracy = sum(1 for y, q in zip(y_true, p) if (q > 0.5) == (y == 1)) / n
    return {"log_loss": round(logloss, 4), "brier": round(brier, 4), "accuracy": round(accuracy, 4), "samples": n}
