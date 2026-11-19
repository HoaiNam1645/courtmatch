"""Lich su thanh tich cua tung nguoi choi theo thoi gian, de dung anh chup dac trung TAI mot thoi diem T chi tu cac tran TRUOC T
(khong ro ri tuong lai, 2.2.12)."""

from __future__ import annotations

import bisect
import statistics
from dataclasses import dataclass
from datetime import datetime

from .data import Dataset

INITIAL_RATING = 1000.0
PRIOR_WINS, PRIOR_GAMES = 2.5, 5.0  # lam tron ty le thang giong V20: (thang + 2,5) / (tran + 5)


@dataclass
class Snapshot:
    rating: float
    win_rate: float  # 0-100, da lam tron
    matches: int
    stability: float | None
    recency_days: float | None
    avg_point_diff: float | None


class Timelines:
    def __init__(self, dataset: Dataset):
        self._times: dict[str, list[datetime]] = {}
        self._rows: dict[str, list[tuple[float, bool, float]]] = {}
        for match in dataset.matches:
            if not match.rated:
                continue
            s1 = sum(g[0] for g in match.games)
            s2 = sum(g[1] for g in match.games)
            for team_no, team in ((1, match.team1), (2, match.team2)):
                diff = (s1 - s2) if team_no == 1 else (s2 - s1)
                for uid in team:
                    rating = dataset.rating_after.get((uid, match.id))
                    prev = self._rows[uid][-1][0] if self._rows.get(uid) else INITIAL_RATING
                    self._times.setdefault(uid, []).append(match.t)
                    self._rows.setdefault(uid, []).append((rating if rating is not None else prev, match.winner == team_no, float(diff)))

    def snapshot(self, user_id: str, at: datetime) -> Snapshot:
        times = self._times.get(user_id, [])
        n = bisect.bisect_left(times, at)
        if n == 0:
            return Snapshot(INITIAL_RATING, 100.0 * PRIOR_WINS / PRIOR_GAMES, 0, None, None, None)
        rows = self._rows[user_id][:n]
        wins = sum(1 for _, won, _ in rows if won)
        diffs = [d for _, _, d in rows]
        return Snapshot(
            rating=rows[-1][0],
            win_rate=round(100.0 * (wins + PRIOR_WINS) / (n + PRIOR_GAMES), 2),
            matches=n,
            stability=round(statistics.pstdev(diffs), 2) if n >= 3 else None,
            recency_days=max(0.0, (at - times[n - 1]).total_seconds() / 86400.0),
            avg_point_diff=round(sum(diffs) / n, 2),
        )
