"""Doc du lieu huan luyen truc tiep tu cac bang cua he thong (2.2.12): moi nguoi choi duoc coi nhu nhau, khong co duong
rieng cho du lieu seed. Ket noi chi-doc."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime

import psycopg

from . import config


@dataclass
class Player:
    user_id: str
    email: str
    gender: str | None
    play_type: str | None
    style: str | None
    lat: float | None
    lng: float | None
    radius_km: float | None
    slots: list[tuple[int, int, int]] = field(default_factory=list)


@dataclass
class MatchRec:
    id: str
    match_type: str
    t: datetime
    team1: tuple[str, ...]
    team2: tuple[str, ...]
    games: list[tuple[int, int]]
    winner: int
    rated: bool


@dataclass
class Dataset:
    players: dict[str, Player]
    matches: list[MatchRec]
    rating_after: dict[tuple[str, str], float]  # (user_id, match_id) -> rating sau tran


def load(url: str | None = None) -> Dataset:
    with psycopg.connect(url or config.database_url(), autocommit=True) as conn:
        conn.execute("SET default_transaction_read_only = on")
        players = _players(conn)
        matches = _matches(conn)
        rating_after = {(u, m): float(r) for u, m, r in conn.execute(
            "SELECT user_id::text, match_id::text, new_rating FROM player_rating_history WHERE match_id IS NOT NULL")}
    return Dataset(players, matches, rating_after)


def _players(conn) -> dict[str, Player]:
    rows = conn.execute("""
        SELECT u.id::text, coalesce(u.email, ''), pp.gender, pp.preferred_play_type, pp.playing_style,
               loc.latitude, loc.longitude, loc.radius_km
        FROM users u JOIN player_profiles pp ON pp.user_id = u.id
        LEFT JOIN LATERAL (SELECT latitude, longitude, radius_km FROM player_preferred_locations
                           WHERE user_id = u.id AND latitude IS NOT NULL ORDER BY is_default DESC, created_at LIMIT 1) loc ON true
        WHERE u.status = 'active'
    """).fetchall()
    players = {r[0]: Player(r[0], r[1], r[2], r[3], r[4], _f(r[5]), _f(r[6]), _f(r[7])) for r in rows}
    for user_id, day, start, end in conn.execute(
            "SELECT user_id::text, day_of_week, start_time, end_time FROM player_availability_slots"):
        if user_id in players:
            players[user_id].slots.append((int(day), start.hour * 60 + start.minute, end.hour * 60 + end.minute))
    return players


def _matches(conn) -> list[MatchRec]:
    by_match: dict[str, dict] = {}
    for mid, mtype, at, excluded, winner in conn.execute("""
        SELECT m.id::text, m.match_type, m.scheduled_at, m.excluded_from_rating, r.winning_team
        FROM matches m JOIN match_results r ON r.match_id = m.id
        WHERE m.status = 'completed' AND m.scheduled_at IS NOT NULL ORDER BY m.scheduled_at, m.id"""):
        by_match[mid] = {"type": mtype, "t": at, "rated": not excluded, "winner": int(winner), "t1": [], "t2": [], "games": []}
    for mid, uid, team in conn.execute("SELECT match_id::text, user_id::text, team_no FROM match_players ORDER BY position_no NULLS LAST, id"):
        if mid in by_match:
            by_match[mid]["t1" if team == 1 else "t2"].append(uid)
    for mid, s1, s2 in conn.execute("SELECT match_id::text, team1_score, team2_score FROM match_games ORDER BY match_id, game_no"):
        if mid in by_match:
            by_match[mid]["games"].append((int(s1), int(s2)))
    return [MatchRec(mid, v["type"], v["t"], tuple(v["t1"]), tuple(v["t2"]), v["games"], v["winner"], v["rated"])
            for mid, v in by_match.items() if v["t1"] and v["t2"] and v["games"]]


def _f(value) -> float | None:
    return None if value is None else float(value)
