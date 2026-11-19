"""Dung tap huan luyen / kiem dinh / kiem thu theo thoi gian (2.2.11 - 2.2.12).

Xep hang doi tac: moi "nhom" la mot nguoi tim tai thoi diem T. Dac trung tinh CHI tu cac tran truoc T; nhan lay tu cac tran
trong (T, T + WINDOW_DAYS]: tran sat nut diem cao, tran mot chieu diem thap; ung vien hop le chua tung gap la mau am (muc 0).
Chia dong theo T: dau -> huan luyen, giua -> kiem dinh, cuoi -> kiem thu; khong chia ngau nhien.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from datetime import datetime, timedelta

from .data import Dataset, MatchRec, Player
from .features import PARTNER_FEATURES, TEAM_FEATURES
from .history import Snapshot, Timelines
from .pairs import baseline_score, partner_features, swap_teams, team_features, vector

STEP_DAYS = 30
WINDOW_DAYS = 60
MIN_HISTORY_DAYS = 90
NEG_PER_GROUP = 20
MAX_RATING_GAP = 400.0  # ung vien am chi lay trong khoang Elo hop ly (giong bo loc khoang Elo cua tim doi tac)
MIN_TRAIN_MATCHES = 3  # nguoi duoi 3 tran khong lam mau huan luyen chinh (training_eligible)
REQUEST_MINUTES = 90
SPLIT = (0.70, 0.15, 0.15)


@dataclass
class Row:
    group: int
    candidate: str
    label: int
    values: dict
    baseline: float
    skill_only: float


@dataclass
class Group:
    id: int
    requester: str
    at: datetime
    segment: str  # single | double
    newbie: bool
    eligible: bool  # du dieu kien lam mau huan luyen chinh
    rows: list[Row]


def _closeness_grade(match: MatchRec, same_team: bool, user: str) -> int:
    s1 = sum(g[0] for g in match.games)
    s2 = sum(g[1] for g in match.games)
    if same_team:
        won = (match.winner == 1) == (user in match.team1)
        return 3 if won else 2
    margin = abs(s1 - s2) / max(1, s1 + s2)
    three_sets = len(match.games) >= 3
    if margin <= 0.06 or (three_sets and margin <= 0.10):
        return 4
    if margin <= 0.12:
        return 3
    if margin <= 0.20:
        return 2
    return 1


def _grades(match: MatchRec) -> dict[tuple[str, str], int]:
    """Muc nhan cua moi cap (a, b) da choi trong tran nay (hai chieu)."""
    out: dict[tuple[str, str], int] = {}
    everyone = list(match.team1) + list(match.team2)
    for a in everyone:
        for b in everyone:
            if a == b:
                continue
            same = (a in match.team1) == (b in match.team1)
            out[(a, b)] = _closeness_grade(match, same, a)
    return out


def build_partner_groups(data: Dataset, seed: int = 42) -> list[Group]:
    rng = random.Random(seed)
    timelines = Timelines(data)
    rated = [m for m in data.matches if m.rated]
    if not rated:
        return []
    start = rated[0].t + timedelta(days=MIN_HISTORY_DAYS)
    end = rated[-1].t - timedelta(days=WINDOW_DAYS)
    pair_grades: dict[tuple[str, str], list[tuple[datetime, int]]] = {}
    for match in rated:
        for key, grade in _grades(match).items():
            pair_grades.setdefault(key, []).append((match.t, grade))

    players = {pid: p for pid, p in data.players.items() if p.lat is not None and p.slots}
    groups: list[Group] = []
    t = start
    gid = 0
    while t <= end:
        until = t + timedelta(days=WINDOW_DAYS)
        snaps = {pid: timelines.snapshot(pid, t) for pid in players}
        for rid, r in players.items():
            rs = snaps[rid]
            if rs.matches == 0:
                continue
            positives: dict[str, int] = {}
            for cid in players:
                if cid == rid:
                    continue
                grades = [g for at, g in pair_grades.get((rid, cid), []) if t < at <= until]
                if grades:
                    positives[cid] = max(grades)
            if not positives:
                continue
            pool = [cid for cid in players if cid != rid and cid not in positives
                    and abs(snaps[cid].rating - rs.rating) <= MAX_RATING_GAP]
            negatives = rng.sample(pool, min(NEG_PER_GROUP, len(pool)))
            # Khung gio yeu cau gia lap: mot khung trong lich ranh cua nguoi tim, keo dai REQUEST_MINUTES.
            day, s0, e0 = rng.choice(r.slots)
            requested = (day, s0, min(e0, s0 + REQUEST_MINUTES))
            rows = []
            for cid, label in [(c, g) for c, g in positives.items()] + [(c, 0) for c in negatives]:
                values = partner_features(r, rs, players[cid], snaps[cid], requested)
                rows.append(Row(gid, cid, label, values, baseline_score(values), values["skill_similarity"]))
            segment = "single" if r.play_type == "single" else "double"
            groups.append(Group(gid, rid, t, segment, rs.matches < 5, rs.matches >= MIN_TRAIN_MATCHES, rows))
            gid += 1
        t += timedelta(days=STEP_DAYS)
    return groups


def split_by_time(groups: list[Group]) -> dict[str, list[Group]]:
    times = sorted({g.at for g in groups})
    a = times[int(len(times) * SPLIT[0])]
    b = times[min(len(times) - 1, int(len(times) * (SPLIT[0] + SPLIT[1])))]
    return {
        "train": [g for g in groups if g.at < a],
        "valid": [g for g in groups if a <= g.at < b],
        "test": [g for g in groups if g.at >= b],
    }


def partner_matrix(groups: list[Group], eligible_only: bool = False):
    import numpy as np
    chosen = [g for g in groups if (g.eligible or not eligible_only)]
    x, y, gid = [], [], []
    for g in chosen:
        for row in g.rows:
            x.append(vector(row.values, PARTNER_FEATURES))
            y.append(row.label)
            gid.append(g.id)
    return np.array(x, dtype=float), np.array(y, dtype=float), np.array(gid), chosen


@dataclass
class TeamRow:
    at: datetime
    match_type: str
    segment: str
    values: dict
    label: int  # 1 neu doi 1 thang


def build_team_rows(data: Dataset) -> list[TeamRow]:
    timelines = Timelines(data)
    rows: list[TeamRow] = []
    for m in data.matches:
        if not m.rated or len(m.team1) != 2 or len(m.team2) != 2:
            continue
        if any(pid not in data.players for pid in m.team1 + m.team2):
            continue
        t1 = [(data.players[p], timelines.snapshot(p, m.t)) for p in m.team1]
        t2 = [(data.players[p], timelines.snapshot(p, m.t)) for p in m.team2]
        mixed = m.match_type == "mixed_double"
        genders = {data.players[p].gender for p in m.team1 + m.team2}
        segment = "mixed" if mixed else ("double_male" if genders == {"male"} else "double_female" if genders == {"female"} else "double_other")
        rows.append(TeamRow(m.t, m.match_type, segment, team_features(t1, t2, mixed), 1 if m.winner == 1 else 0))
    return rows


def split_team_rows(rows: list[TeamRow]) -> dict[str, list[TeamRow]]:
    ordered = sorted(rows, key=lambda r: r.at)
    n = len(ordered)
    a, b = int(n * SPLIT[0]), int(n * (SPLIT[0] + SPLIT[1]))
    return {"train": ordered[:a], "valid": ordered[a:b], "test": ordered[b:]}


def team_matrix(rows: list[TeamRow], augment: bool = False):
    import numpy as np
    x, y = [], []
    for row in rows:
        x.append(vector(row.values, TEAM_FEATURES))
        y.append(row.label)
        if augment:
            x.append(vector(swap_teams(row.values), TEAM_FEATURES))
            y.append(1 - row.label)
    return np.array(x, dtype=float), np.array(y, dtype=float)
