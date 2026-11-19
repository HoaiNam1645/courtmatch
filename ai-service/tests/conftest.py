import random
from datetime import datetime, timedelta, timezone

import pytest

from courtly_ai.data import Dataset, MatchRec, Player


def synthetic(n_players: int = 40, n_matches: int = 900, seed: int = 1) -> Dataset:
    rng = random.Random(seed)
    styles = ["attacking", "balanced", "defensive"]
    players = {}
    for i in range(n_players):
        players[f"p{i}"] = Player(f"p{i}", f"p{i}@example.com", "male" if i % 2 else "female", rng.choice(["single", "double", "mixed"]),
                                  rng.choice(styles), 16.04 + rng.uniform(-0.03, 0.03), 108.2 + rng.uniform(-0.03, 0.03),
                                  rng.choice([3.0, 5.0, 10.0]), [(rng.randint(1, 7), 18 * 60, 21 * 60), (rng.randint(1, 7), 7 * 60, 10 * 60)])
    rating = {pid: 1000.0 + rng.uniform(-200, 200) for pid in players}
    skill = dict(rating)
    start = datetime(2024, 1, 1, 9, tzinfo=timezone.utc)
    matches, rating_after = [], {}
    ids = list(players)
    for k in range(n_matches):
        t = start + timedelta(hours=k * 18)
        doubles = k % 2 == 0
        picked = rng.sample(ids, 4 if doubles else 2)
        team1, team2 = tuple(picked[: len(picked) // 2]), tuple(picked[len(picked) // 2:])
        e1 = sum(skill[p] for p in team1) / len(team1)
        e2 = sum(skill[p] for p in team2) / len(team2)
        p1 = 1 / (1 + 10 ** ((e2 - e1) / 400))
        winner = 1 if rng.random() < p1 else 2
        games = [(21, rng.randint(10, 19)) if winner == 1 else (rng.randint(10, 19), 21) for _ in range(2)]
        mid = f"m{k}"
        delta = 16 * ((1 if winner == 1 else 0) - p1)
        for team, sign in ((team1, 1), (team2, -1)):
            for p in team:
                rating[p] = max(100.0, rating[p] + sign * delta)
                rating_after[(p, mid)] = rating[p]
        matches.append(MatchRec(mid, "double" if doubles else "single", t, team1, team2, games, winner, True))
    return Dataset(players, matches, rating_after)


@pytest.fixture(scope="session")
def data() -> Dataset:
    return synthetic()
