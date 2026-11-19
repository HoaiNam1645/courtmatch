"""Dac trung cua mot cap tu hai anh chup + ho so, theo schema trong features.py."""

from __future__ import annotations

import math
import random

from . import compat
from .data import Player
from .features import PARTNER_FEATURES, TEAM_FEATURES
from .history import Snapshot

PROVISIONAL_LIMIT = 5
NAN = float("nan")


def _n(value) -> float:
    return NAN if value is None else float(value)


def partner_features(r: Player, rs: Snapshot, c: Player, cs: Snapshot, requested: compat.Slot | None) -> dict:
    distance = None
    if None not in (r.lat, r.lng, c.lat, c.lng):
        distance = compat.haversine_km(r.lat, r.lng, c.lat, c.lng)
    skill = compat.skill_similarity(rs.rating, cs.rating)
    location = compat.location_compat(distance, r.radius_km)
    time = compat.time_compat(r.slots, c.slots, requested)
    style = compat.style_compat(r.style, c.style)
    values = {
        "r_rating": rs.rating, "c_rating": cs.rating, "rating_gap": abs(rs.rating - cs.rating),
        "r_win_rate": rs.win_rate, "c_win_rate": cs.win_rate,
        "r_matches": rs.matches, "c_matches": cs.matches,
        "r_stability": rs.stability, "c_stability": cs.stability,
        "r_recency_days": rs.recency_days, "c_recency_days": cs.recency_days,
        "r_avg_point_diff": rs.avg_point_diff, "c_avg_point_diff": cs.avg_point_diff,
        "skill_similarity": skill, "location_score": location, "time_score": time, "style_score": style,
        "distance_km": distance,
        "same_play_type": 1.0 if (r.play_type and r.play_type == c.play_type) else 0.0,
        "r_provisional": 1.0 if rs.matches < PROVISIONAL_LIMIT else 0.0,
        "c_provisional": 1.0 if cs.matches < PROVISIONAL_LIMIT else 0.0,
    }
    return values


def vector(values: dict, names: list[str]) -> list[float]:
    return [_n(values.get(name)) for name in names]


def baseline_score(values: dict) -> float:
    """Diem baseline cua mot cap tu chinh cac diem thanh phan da tinh (giong PartnerScoring)."""
    return compat.baseline_total(values.get("skill_similarity"), values.get("style_score"),
                                 values.get("time_score"), values.get("location_score"))


def pair_compat(a: Player, b: Player) -> float | None:
    """Do tuong thich trong doi: trung binh phong cach, trung lich ranh, gan nhau (bo thanh phan thieu)."""
    parts = [compat.style_compat(a.style, b.style)]
    overlap = compat.weekly_overlap(a.slots, b.slots)
    if overlap is not None:
        parts.append(overlap)
    if None not in (a.lat, a.lng, b.lat, b.lng):
        radius = min(r for r in (a.radius_km, b.radius_km) if r) if (a.radius_km and b.radius_km) else None
        location = compat.location_compat(compat.haversine_km(a.lat, a.lng, b.lat, b.lng), radius)
        if location is not None:
            parts.append(location)
    return round(sum(parts) / len(parts), 2) if parts else None


def _mean(values: list) -> float | None:
    clean = [v for v in values if v is not None]
    return sum(clean) / len(clean) if clean else None


def team_features(t1: list[tuple[Player, Snapshot]], t2: list[tuple[Player, Snapshot]], mixed: bool) -> dict:
    def block(team):
        ratings = [s.rating for _, s in team]
        return {
            "rating": sum(ratings) / len(ratings),
            "gap": abs(ratings[0] - ratings[1]) if len(ratings) == 2 else 0.0,
            "win_rate": sum(s.win_rate for _, s in team) / len(team),
            "min_matches": min(s.matches for _, s in team),
            "stability": _mean([s.stability for _, s in team]),
            "compat": pair_compat(team[0][0], team[1][0]) if len(team) == 2 else None,
            "recency": _mean([s.recency_days for _, s in team]),
        }
    a, b = block(t1), block(t2)
    return {
        "t1_rating": a["rating"], "t2_rating": b["rating"], "rating_diff": a["rating"] - b["rating"],
        "t1_partner_gap": a["gap"], "t2_partner_gap": b["gap"], "t1_win_rate": a["win_rate"], "t2_win_rate": b["win_rate"],
        "t1_min_matches": a["min_matches"], "t2_min_matches": b["min_matches"],
        "t1_stability": a["stability"], "t2_stability": b["stability"],
        "t1_pair_compat": a["compat"], "t2_pair_compat": b["compat"],
        "t1_recency_days": a["recency"], "t2_recency_days": b["recency"], "is_mixed": 1.0 if mixed else 0.0,
    }


def swap_teams(values: dict) -> dict:
    """Doi cho hai doi (nhan doi dau): dung cho tang cuong doi xung khi huan luyen."""
    out = dict(values)
    for key in ("rating", "partner_gap", "win_rate", "min_matches", "stability", "pair_compat", "recency_days"):
        out[f"t1_{key}"], out[f"t2_{key}"] = values[f"t2_{key}"], values[f"t1_{key}"]
    out["rating_diff"] = -values["rating_diff"]
    return out


def elo_win_probability(rating_diff: float) -> float:
    return 1.0 / (1.0 + math.pow(10.0, -rating_diff / 400.0))


__all__ = ["partner_features", "team_features", "swap_teams", "vector", "baseline_score", "pair_compat",
           "elo_win_probability", "PARTNER_FEATURES", "TEAM_FEATURES", "random"]
