"""Diem tuong thich 0-100, cong lai CHINH XAC cong thuc cua Spring Boot (PartnerSearchService) de huan luyen va suy luan
nhin thay cung mot dac trung; va baseline tong the (PartnerScoring)."""

import math

SKILL_W, STYLE_W, TIME_W, LOCATION_W = 0.4, 0.2, 0.2, 0.2


def skill_similarity(requester_rating: float, candidate_rating: float) -> float:
    expected = 1.0 / (1.0 + 10.0 ** ((candidate_rating - requester_rating) / 400.0))
    return round(100.0 * (1.0 - abs(2.0 * expected - 1.0)), 2)


def location_compat(distance_km: float | None, radius_km: float | None) -> float | None:
    if distance_km is None or radius_km is None:
        return None
    if radius_km <= 0:
        return 0.0
    return round(min(100.0, max(0.0, 100.0 * (1.0 - distance_km / radius_km))), 2)


def style_compat(a: str | None, b: str | None) -> float:
    if a is None or b is None:
        return 50.0
    if a == b:
        return 100.0
    if a == "balanced" or b == "balanced":
        return 80.0
    return 65.0


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6371.0088
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


# Lich ranh: danh sach (thu, phut bat dau, phut ket thuc).
Slot = tuple[int, int, int]


def _merge(ranges: list[tuple[int, int]]) -> list[tuple[int, int]]:
    merged: list[tuple[int, int]] = []
    for start, end in sorted(ranges):
        if merged and start <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(merged[-1][1], end))
        else:
            merged.append((start, end))
    return merged


def _weekly(slots: list[Slot]) -> dict[int, list[tuple[int, int]]]:
    days: dict[int, list[tuple[int, int]]] = {}
    for day, start, end in slots:
        days.setdefault(day, []).append((start, end))
    return {day: _merge(items) for day, items in days.items()}


def _overlap(left: list[tuple[int, int]], right: list[tuple[int, int]]) -> int:
    total = 0
    for a_start, a_end in left:
        for b_start, b_end in right:
            total += max(0, min(a_end, b_end) - max(a_start, b_start))
    return total


def time_compat(requester_slots: list[Slot], candidate_slots: list[Slot], requested: Slot | None) -> float | None:
    """Ty le lich ranh cua nguoi tim (cong khung gio yeu cau) duoc ung vien bao phu; None neu thieu lich."""
    schedule = _weekly(requester_slots + ([requested] if requested else []))
    total = sum(end - start for ranges in schedule.values() for start, end in ranges)
    if total == 0 or not candidate_slots:
        return None
    candidate = _weekly(candidate_slots)
    overlap = sum(_overlap(ranges, candidate.get(day, [])) for day, ranges in schedule.items())
    return round(min(100.0, max(0.0, 100.0 * overlap / total)), 2)


def weekly_overlap(first: list[Slot], second: list[Slot]) -> float | None:
    """Ty le gio ranh trung nhau so voi nguoi it gio hon (dung cho cap trong doi); None neu mot ben chua khai bao."""
    left, right = _weekly(first), _weekly(second)
    left_minutes = sum(e - s for r in left.values() for s, e in r)
    right_minutes = sum(e - s for r in right.values() for s, e in r)
    smaller = min(left_minutes, right_minutes)
    if smaller == 0:
        return None
    overlap = sum(_overlap(ranges, right.get(day, [])) for day, ranges in left.items())
    return round(min(100.0, max(0.0, 100.0 * overlap / smaller)), 2)


def baseline_total(skill: float | None, style: float | None, time: float | None, location: float | None) -> float:
    """Trung binh co trong so, thanh phan None bi bo va chuan hoa lai trong so (giong PartnerScoring.total)."""
    total = weight = 0.0
    for value, w in ((skill, SKILL_W), (style, STYLE_W), (time, TIME_W), (location, LOCATION_W)):
        if value is not None:
            total += w * value
            weight += w
    return round(total / weight, 2) if weight else 0.0
